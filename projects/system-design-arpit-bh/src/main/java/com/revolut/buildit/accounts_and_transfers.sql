create table accounts(
    id bigint not null generated always as identity primary key,
    owner_id bigint not null,
    balance bigint not null check ( balance>=0 ),
    version bigint not null default 0, -- for optimistic locking
    currency char(3) not null,
    created_at timestamptz not null default now()
);

-- create transfers table
create table transfers(
    id bigint not null generated always as identity primary key,
    idempotency_key text not null unique ,
    from_account_id bigint not null references accounts(id),
    to_account_id bigint not null references accounts(id),
    amount bigint not null check(amount>0),
    created_at timestamptz not null default now(),
    check ( from_account_id<>to_account_id)
);

-- inserting to table

insert into accounts (owner_id, balance, currency) VALUES
                                                       (1, 100, 'EUR'),
                                                       (2, 200, 'EUR'),
                                                       (3, 1000, 'EUR');


-- creating indexes
create index idx on transfers (from_account_id, created_at desc); -- for queries which search for transfers of a particular user based on recent transactions
create index idx2 on transfers (to_account_id, created_at desc);

-- explain and analyze
explain analyze
select * from transfers where from_account_id = 1
order by created_at
limit 20;


-- pessimistic locking and transactions

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

--- end of transaction



