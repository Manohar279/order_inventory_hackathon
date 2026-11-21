-- Seed data from section 5 of the brief. Reloaded on every application start.
INSERT INTO products (id, name, price) VALUES (101, 'Laptop',     50000.00);
INSERT INTO products (id, name, price) VALUES (102, 'Mouse',        800.00);
INSERT INTO products (id, name, price) VALUES (103, 'Keyboard',    1500.00);
INSERT INTO products (id, name, price) VALUES (104, 'Monitor',    12000.00);
INSERT INTO products (id, name, price) VALUES (105, 'Headphones',  2000.00);

INSERT INTO inventory (product_id, available_qty) VALUES (101, 10);
INSERT INTO inventory (product_id, available_qty) VALUES (102, 25);
INSERT INTO inventory (product_id, available_qty) VALUES (103, 15);
INSERT INTO inventory (product_id, available_qty) VALUES (104, 5);
INSERT INTO inventory (product_id, available_qty) VALUES (105, 0);
