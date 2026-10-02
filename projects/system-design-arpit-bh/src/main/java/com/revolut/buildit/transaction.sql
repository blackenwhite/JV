begin;

select id, balance from accounts
where id in (1,2)
order by id
for update;

-- app checks balance>=amount

update accounts set balance = balance-100 where id = 1;
update accounts set balance = balance+100 where id = 2;

insert into transfers(idempotency_key, from_account_id, to_account_id, amount) values ('abc-123', 1, 2, 100);

commit;

