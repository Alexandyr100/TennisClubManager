CREATE TYPE user_role AS ENUM ('player', 'coach', 'admin');

CREATE TYPE match_status AS ENUM ('open', 'accepted', 'pending_confirmation', 'completed', 'cancelled');

CREATE TYPE group_training_status AS ENUM ('scheduled', 'cancelled');

CREATE TYPE enrollment_status AS ENUM ('main', 'reserve', 'cancelled');

CREATE TYPE individual_training_status AS ENUM ('available', 'booked', 'cancelled');

CREATE TYPE notification_status AS ENUM ('pending', 'sent', 'failed');

CREATE TABLE clubs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(150) NOT NULL,
    address varchar(255) NOT NULL,
    latitude double precision,
    longitude double precision,
    owner_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_clubs_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_clubs_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id uuid NOT NULL REFERENCES clubs(id),
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'player',
    elo_rating integer NOT NULL DEFAULT 1000,
    privacy_consent_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE clubs
    ADD CONSTRAINT fk_clubs_owner
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE matches (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id uuid NOT NULL REFERENCES clubs(id),
    creator_id uuid NOT NULL REFERENCES users(id),
    opponent_id uuid REFERENCES users(id),
    scheduled_date date NOT NULL,
    scheduled_time time NOT NULL,
    level varchar(50) NOT NULL,
    status match_status NOT NULL DEFAULT 'open',
    score varchar(50),
    winner_id uuid REFERENCES users(id),
    confirmed_by_admin_id uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_matches_distinct_players CHECK (opponent_id IS NULL OR opponent_id <> creator_id),
    CONSTRAINT chk_matches_valid_winner CHECK (winner_id IS NULL OR winner_id IN (creator_id, opponent_id))
);

CREATE TABLE group_trainings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id uuid NOT NULL REFERENCES clubs(id),
    coach_id uuid NOT NULL REFERENCES users(id),
    scheduled_date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    level varchar(50) NOT NULL,
    status group_training_status NOT NULL DEFAULT 'scheduled',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_group_trainings_time CHECK (end_time > start_time)
);

CREATE TABLE group_enrollments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    group_training_id uuid NOT NULL REFERENCES group_trainings(id) ON DELETE CASCADE,
    player_id uuid NOT NULL REFERENCES users(id),
    status enrollment_status NOT NULL,
    queue_position integer NOT NULL,
    enrolled_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_group_enrollments_queue CHECK (queue_position >= 1)
);

CREATE TABLE individual_trainings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id uuid NOT NULL REFERENCES clubs(id),
    coach_id uuid NOT NULL REFERENCES users(id),
    player_id uuid REFERENCES users(id),
    scheduled_date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    status individual_training_status NOT NULL DEFAULT 'available',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_individual_trainings_time CHECK (end_time > start_time),
    CONSTRAINT chk_individual_trainings_booking CHECK (
        (status = 'booked' AND player_id IS NOT NULL)
        OR (status <> 'booked' AND player_id IS NULL)
    )
);

CREATE TABLE email_notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id uuid NOT NULL REFERENCES clubs(id),
    user_id uuid NOT NULL REFERENCES users(id),
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    status notification_status NOT NULL DEFAULT 'pending',
    retry_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    CONSTRAINT chk_email_notifications_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_users_club_elo ON users (club_id, elo_rating DESC);

CREATE INDEX idx_matches_club_date ON matches (club_id, scheduled_date);
CREATE INDEX idx_matches_club_status ON matches (club_id, status);

CREATE INDEX idx_group_trainings_club_date ON group_trainings (club_id, scheduled_date);
CREATE INDEX idx_group_trainings_coach_date ON group_trainings (coach_id, scheduled_date);

CREATE UNIQUE INDEX uq_group_enrollments_active
    ON group_enrollments (group_training_id, player_id)
    WHERE status IN ('main', 'reserve');
CREATE INDEX idx_group_enrollments_training_queue ON group_enrollments (group_training_id, status, queue_position);
CREATE INDEX idx_group_enrollments_player_status ON group_enrollments (player_id, status);

CREATE INDEX idx_individual_trainings_club_date ON individual_trainings (club_id, scheduled_date);
CREATE INDEX idx_individual_trainings_coach_date ON individual_trainings (coach_id, scheduled_date);
CREATE INDEX idx_individual_trainings_player_date
    ON individual_trainings (player_id, scheduled_date)
    WHERE player_id IS NOT NULL;

CREATE INDEX idx_email_notifications_pending
    ON email_notifications (created_at)
    WHERE status = 'pending' AND retry_count < 3;
