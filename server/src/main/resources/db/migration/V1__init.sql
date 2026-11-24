-- Users are identified by Google's stable `sub` claim, never by email alone.
create table app_user (
    id           uuid primary key,
    google_sub   text        not null unique,
    email        text        not null,
    display_name text,
    created_at   timestamptz not null default now()
);

-- Opaque tokens. Only SHA-256 hashes are stored, so a database leak does not leak sessions.
create table auth_token (
    token_hash bytea       primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    kind       text        not null check (kind in ('ACCESS', 'REFRESH')),
    family_id  uuid        not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at    timestamptz,
    revoked_at timestamptz
);
create index auth_token_family_idx on auth_token (family_id);
create index auth_token_expiry_idx on auth_token (expires_at);

-- Metron cache. Payloads are the shared contract DTOs (ComicDto, CreatorDto, StoryArcDto) as JSON.
create table catalog_issue (
    id         bigint      primary key,
    payload    jsonb       not null,
    fetched_at timestamptz not null default now()
);

-- UPC -> issue. issue_id is null for a cached "not found" (negative cache).
create table catalog_upc (
    code       text        primary key,
    issue_id   bigint      references catalog_issue (id),
    fetched_at timestamptz not null default now()
);

create table catalog_creator (
    id         bigint      primary key,
    payload    jsonb       not null,
    fetched_at timestamptz not null default now()
);

create table catalog_arc (
    id         bigint      primary key,
    payload    jsonb       not null,
    fetched_at timestamptz not null default now()
);

create table collection_entry (
    id               uuid        primary key,
    user_id          uuid        not null references app_user (id) on delete cascade,
    scanned_code     text        not null check (scanned_code ~ '^[0-9]{12}([0-9]{5})?$'),
    status           text        not null check (status in ('PENDING', 'RESOLVED', 'NOT_FOUND')),
    issue_id         bigint      references catalog_issue (id),
    owned_cover_name text,
    attempts         int         not null default 0,
    next_attempt_at  timestamptz not null default now(),
    added_at         timestamptz not null default now()
);
create index collection_entry_user_idx on collection_entry (user_id, added_at);
create index collection_entry_queue_idx on collection_entry (status, next_attempt_at);
