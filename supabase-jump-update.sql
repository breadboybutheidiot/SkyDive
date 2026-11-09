-- Выполни после supabase-profile.sql и supabase-chat.sql.
-- Существующие записи прыжков и сообщений сохраняются.
begin;

alter table public.jumps add column if not exists freefall_seconds integer;
alter table public.jumps drop constraint if exists jumps_freefall_seconds_range;
alter table public.jumps add constraint jumps_freefall_seconds_range
    check (freefall_seconds is null or freefall_seconds between 0 and 86400);

revoke update on public.jumps from public, anon, authenticated;
grant update (jump_date, location, altitude, discipline, notes, freefall_seconds)
    on public.jumps to authenticated;
drop policy if exists "Users can update their jumps" on public.jumps;
create policy "Users can update their jumps" on public.jumps for update to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
drop policy if exists "Restrict updates to own jumps" on public.jumps;
create policy "Restrict updates to own jumps" on public.jumps as restrictive for update to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create or replace function public.update_own_jump(
    jump_id uuid, new_date text, new_location text, new_altitude text,
    new_discipline text, new_notes text, new_freefall_seconds integer
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare result jsonb;
begin
    update public.jumps as j set
        jump_date = new_date, location = new_location, altitude = new_altitude,
        discipline = new_discipline, notes = new_notes, freefall_seconds = new_freefall_seconds
    where j.id = jump_id and j.user_id = (select auth.uid())
    returning to_jsonb(j) into result;
    if result is null then raise exception 'Запись не найдена или недоступна'; end if;
    return result;
end;
$$;
revoke all on function public.update_own_jump(uuid,text,text,text,text,text,integer) from public, anon, authenticated;
grant execute on function public.update_own_jump(uuid,text,text,text,text,text,integer) to authenticated;

-- В чате видны только имя, фото и общие показатели автора, без записей журнала.
create or replace function public.chat_public_profile(profile_user_id uuid)
returns table (display_name text, photo_base64 text, jump_count bigint, freefall_seconds bigint)
language sql stable security definer set search_path = '' as $$
    select coalesce(nullif(p.display_name, ''), case
               when lower(split_part(u.email, '@', 2)) in ('owfsmpsqbwsguejtnyng.supabase.co', 'users.skydive.example')
               then nullif(split_part(u.email, '@', 1), '') else null end, 'Участник'),
           p.photo_base64,
           (select count(*) from public.jumps j where j.user_id = u.id),
           (select sum(j.freefall_seconds) from public.jumps j where j.user_id = u.id)
    from auth.users u
    left join public.skydiver_profiles p on p.user_id = u.id
    where (select auth.uid()) is not null and u.id = profile_user_id
      and exists (select 1 from public.chat_messages m where m.author_id = u.id);
$$;
revoke all on function public.chat_public_profile(uuid) from public, anon, authenticated;
grant execute on function public.chat_public_profile(uuid) to authenticated;

commit;
