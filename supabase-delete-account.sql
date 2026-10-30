-- Удаление строки в LoginAndPass удаляет связанный аккаунт Supabase Auth.
-- Само применение скрипта не удаляет существующие аккаунты.
begin;

create or replace function public.delete_linked_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    -- При удалении аккаунта из Auth каскад сам удаляет строку таблицы.
    -- Повторное удаление Auth в этом случае не требуется.
    if old.user_id is not null and pg_trigger_depth() = 1 then
        delete from auth.users where id = old.user_id;
    end if;
    return old;
end;
$$;
revoke all on function public.delete_linked_auth_user() from public, anon, authenticated;

drop trigger if exists delete_linked_auth_user on public."LoginAndPass";
create trigger delete_linked_auth_user
after delete on public."LoginAndPass"
for each row execute function public.delete_linked_auth_user();

commit;
