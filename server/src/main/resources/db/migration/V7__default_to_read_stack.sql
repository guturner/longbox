-- Create a "To Read" stack for every existing user (idempotent via unique constraint).
insert into container (id, user_id, name, kind, created_at)
select gen_random_uuid(), id, 'To Read', 'STACK', now()
from app_user
on conflict (user_id, name, kind) do nothing;

-- Assign all resolved, unorganised entries to that user's "To Read" stack.
update collection_entry ce
set container_id = c.id
from container c
where c.user_id = ce.user_id
  and c.name    = 'To Read'
  and c.kind    = 'STACK'
  and ce.container_id is null
  and ce.status = 'IN_STACK';
