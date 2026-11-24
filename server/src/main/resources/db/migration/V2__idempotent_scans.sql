-- Offline outbox replays must not create duplicates: the device sends a client-generated id per scan.
alter table collection_entry add column client_mutation_id text;
create unique index collection_entry_client_mutation_idx
    on collection_entry (user_id, client_mutation_id)
    where client_mutation_id is not null;
