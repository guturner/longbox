create table container (
    id         uuid        primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    name       text        not null,
    kind       text        not null check (kind in ('STACK', 'BOX')),
    created_at timestamptz not null default now(),
    unique (user_id, name, kind)
);
create index container_user_idx on container (user_id, created_at);

alter table collection_entry
    add column container_id uuid references container (id) on delete set null;
create index collection_entry_container_idx on collection_entry (container_id);
