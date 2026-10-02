create table users(
    id bigint not null generated always as identity primary key,
    name text not null,
);

create table carts(
    id bigint not null generated always as identity primary key,
    owner_id bigint not null references users(id),
    updated_at timestamptz not null default now()
);

create table cart_items(
    id bigint not null primary key,
    cart_id bigint not null references carts(id),
    owner_id bigint not null references users(id),
    quantity integer not null default 0,
    price bigint not null check(price>0),
    updated_at timestamptz not null default now(),
    version bigint not null default 0 -- if we go ahead with opt locking

);

---indexes
create index on carts (owner_id, updated_at desc);

create index on cart_items (cart_id, owner_id, updated_at desc);

--- locking for updating items
Begin;

select id, cart_id, owner_id from cart_items
where cart_id = 1 and owner_id = 5
for update;

update cart_items set quantity = quantity + 5 where cart_id = 1 and owner_id = 5

commit;