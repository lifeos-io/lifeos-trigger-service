-- Trigger keeps only what it needs to act at the right time: each user's time zone, the jobs it will run, and
-- the active week's numbers. Everything comes from Strategy's events; Trigger never changes the plan.

-- The user's time zone (UserProfileUpdated). Without a row, jobs use Europe/Istanbul like Strategy's default.
CREATE TABLE user_setting (
    user_id    uuid        PRIMARY KEY,
    timezone   text        NOT NULL,
    version    integer     NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL
);

-- Something to publish at a moment: a decision's review date, a reopen trigger's date, the weekly review
-- reminder, the mid-week drift check. The moment is kept as the user's local date and time (due_on, due_time) and
-- as an instant (due_at) computed with their time zone, recomputed when the time zone changes.
CREATE TABLE scheduled_job (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL,
    kind         text        NOT NULL
        CHECK (kind IN ('DECISION_REVIEW', 'REOPEN_TRIGGER', 'WEEKLY_REVIEW', 'DRIFT_CHECK')),
    -- The decision or weekly plan the job is about, and which of its parts (a reopen trigger's position; else 0).
    subject_id   uuid        NOT NULL,
    sub_key      integer     NOT NULL DEFAULT 0 CHECK (sub_key >= 0),
    due_on       date        NOT NULL,
    due_time     time        NOT NULL,
    due_at       timestamptz NOT NULL,
    -- What the published event needs that the job does not hold itself (e.g. a decision's title).
    payload      jsonb       NOT NULL DEFAULT '{}',
    status       text        NOT NULL CHECK (status IN ('PENDING', 'FIRED', 'CANCELLED')),
    fired_at     timestamptz,
    cancelled_at timestamptz,
    version      integer     NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    -- A redelivered event schedules nothing twice.
    UNIQUE (kind, subject_id, sub_key),
    CHECK ((status = 'FIRED') = (fired_at IS NOT NULL)),
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

-- The scheduler's question: which pending jobs are due?
CREATE INDEX scheduled_job_due_idx ON scheduled_job (due_at) WHERE status = 'PENDING';
CREATE INDEX scheduled_job_user_idx ON scheduled_job (user_id, status, due_at);

-- The latest numbers of a committed week (WeeklyPlanActivated, CapacityChanged, ActionStatusChanged), for the
-- drift check. Keyed by Strategy's weekly plan ID.
CREATE TABLE week_snapshot (
    weekly_plan_id   uuid        PRIMARY KEY,
    user_id          uuid        NOT NULL,
    iso_week         text        NOT NULL CHECK (iso_week ~ '^[0-9]{4}-W[0-9]{2}$'),
    week_start       date        NOT NULL,
    capacity_minutes integer     NOT NULL CHECK (capacity_minutes >= 0),
    planned_count    integer     NOT NULL CHECK (planned_count >= 0),
    done_count       integer     NOT NULL CHECK (done_count >= 0),
    deferred_count   integer     NOT NULL CHECK (deferred_count >= 0),
    dropped_count    integer     NOT NULL CHECK (dropped_count >= 0),
    planned_minutes  integer     NOT NULL CHECK (planned_minutes >= 0),
    done_minutes     integer     NOT NULL CHECK (done_minutes >= 0),
    -- CLOSED once the week was reviewed; its jobs are cancelled then.
    status           text        NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    version          integer     NOT NULL DEFAULT 0,
    updated_at       timestamptz NOT NULL
);

CREATE INDEX week_snapshot_user_idx ON week_snapshot (user_id, status);
