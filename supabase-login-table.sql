-- Запусти в Supabase SQL Editor для существующей таблицы public."LoginAndPass".
-- Auth проверяет пароль, а его копия сохраняется в столбце Password.
begin;

alter table public."LoginAndPass"
    add column if not exists user_id uuid references auth.users(id) on delete cascade;

-- Существующий столбец Password больше не обязателен для новых записей.
alter table public."LoginAndPass" alter column "Password" drop not null;
create unique index if not exists login_and_pass_user_id_key on public."LoginAndPass" (user_id);

alter table public."LoginAndPass" enable row level security;
revoke all privileges on table public."LoginAndPass" from public, anon, authenticated;
revoke select ("Password"), insert ("Password"), update ("Password") on public."LoginAndPass" from public, anon, authenticated;
grant select (user_id, "Login") on public."LoginAndPass" to authenticated;
create or replace function public.save_password_copy(password_value text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
    if (select auth.uid()) is null then
        return false;
    end if;
    update public."LoginAndPass" set "Password" = password_value
    where user_id = (select auth.uid());
    return found;
end;
$$;
revoke all on function public.save_password_copy(text) from public, anon, authenticated;
grant execute on function public.save_password_copy(text) to authenticated;

drop policy if exists "Users can see their own login" on public."LoginAndPass";
create policy "Users can see their own login" on public."LoginAndPass"
    for select to authenticated using ((select auth.uid()) = user_id);

create or replace function public.save_skydive_login()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if lower(new.email) like '%@owfsmpsqbwsguejtnyng.supabase.co' or lower(new.email) like '%@users.skydive.example' then
        insert into public."LoginAndPass" (user_id, "Login")
        values (new.id, split_part(lower(new.email), '@', 1))
        on conflict (user_id) do update set "Login" = excluded."Login";
    end if;
    return new;
end;
$$;
revoke all on function public.save_skydive_login() from public, anon, authenticated;

drop trigger if exists save_skydive_login on auth.users;
create trigger save_skydive_login
after insert or update of email on auth.users
for each row execute function public.save_skydive_login();

-- Добавляет уже зарегистрированные через логин аккаунты.
insert into public."LoginAndPass" (user_id, "Login")
select id, split_part(lower(email), '@', 1)
from auth.users
where lower(email) like '%@owfsmpsqbwsguejtnyng.supabase.co' or lower(email) like '%@users.skydive.example'
on conflict (user_id) do update set "Login" = excluded."Login";

commit;
