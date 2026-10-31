-- Appliquer sur une base PostgreSQL dédiée à Rush Studio.
-- Ne stocker ni les données bancaires ni les jetons d'achat en clair.
create table if not exists rush_premium_purchases (
  purchase_token_sha256 text primary key,
  product_id text not null check (product_id = 'rush_premium'),
  first_verified_at timestamptz not null default now(),
  last_verified_at timestamptz not null default now()
);
revoke all on rush_premium_purchases from public;
