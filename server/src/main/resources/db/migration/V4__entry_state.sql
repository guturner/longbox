-- Rename status values: RESOLVED → IN_STACK, NOT_FOUND → LOOKUP_FAILED.
-- IN_BOX and DELETED are new values used starting FEAT-15.
alter table collection_entry drop constraint collection_entry_status_check;
update collection_entry set status = 'IN_STACK'      where status = 'RESOLVED';
update collection_entry set status = 'LOOKUP_FAILED' where status = 'NOT_FOUND';
alter table collection_entry add constraint collection_entry_status_check
    check (status in ('PENDING', 'LOOKUP_FAILED', 'IN_STACK', 'IN_BOX', 'DELETED'));
