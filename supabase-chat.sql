-- Сначала должен быть выполнен supabase-profile.sql.
-- Выполни этот скрипт в SQL Editor проекта Supabase.
begin;

create table if not exists public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    author_id uuid default auth.uid() references auth.users(id) on delete set null,
    body text not null check (char_length(btrim(body)) between 1 and 500),
    created_at timestamptz not null default now()
);
create index if not exists chat_messages_created_idx on public.chat_messages (created_at desc, id desc);
create index if not exists chat_messages_author_idx on public.chat_messages (author_id);
alter table public.chat_messages enable row level security;
revoke all on public.chat_messages from public, anon, authenticated;
revoke update (id, author_id, body, created_at) on public.chat_messages from public, anon, authenticated;
grant select on public.chat_messages to authenticated;
grant insert (body) on public.chat_messages to authenticated;

drop policy if exists "Read chat messages" on public.chat_messages;
create policy "Read chat messages" on public.chat_messages
    for select to authenticated using (true);
drop policy if exists "Send own chat messages" on public.chat_messages;
create policy "Send own chat messages" on public.chat_messages
    for insert to authenticated with check ((select auth.uid()) = author_id);

-- Возвращаются только профили авторов, которые писали в чат.
create or replace function public.chat_profiles(author_ids uuid[])
returns table (user_id uuid, display_name text, photo_base64 text)
language sql stable security definer set search_path = '' as $$
    select u.id,
           coalesce(nullif(p.display_name, ''), case
               when lower(split_part(u.email, '@', 2)) in ('owfsmpsqbwsguejtnyng.supabase.co', 'users.skydive.example')
               then nullif(split_part(u.email, '@', 1), '')
               else null
           end, 'Участник'),
           p.photo_base64
    from auth.users u
    left join public.skydiver_profiles p on p.user_id = u.id
    where (select auth.uid()) is not null
      and coalesce(array_length(author_ids, 1), 0) <= 50
      and u.id = any(author_ids)
      and exists (select 1 from public.chat_messages m where m.author_id = u.id);
$$;
revoke all on function public.chat_profiles(uuid[]) from public, anon, authenticated;
grant execute on function public.chat_profiles(uuid[]) to authenticated;

commit;
