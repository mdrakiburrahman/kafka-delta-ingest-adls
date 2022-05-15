USE testDB
GO

-- = = = = = = = = = = = = =
-- server1.dbo.customers
-- = = = = = = = = = = = = =
-- Read
SELECT * FROM customers;

-- CREATE
INSERT INTO customers (first_name, last_name, email) VALUES ('Delora', 'Autin', 'dautin0@facebook.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Ingram', 'McDill', 'imcdill1@tuttocitta.it');
INSERT INTO customers (first_name, last_name, email) VALUES ('Alyson', 'Renison', 'arenison2@opensource.org');
INSERT INTO customers (first_name, last_name, email) VALUES ('Lita', 'Peabody', 'lpeabody3@youtube.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Fern', 'Tebbet', 'ftebbet4@ebay.co.uk');
INSERT INTO customers (first_name, last_name, email) VALUES ('Camella', 'Fance', 'cfance5@smugmug.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Morey', 'Bruineman', 'mbruineman6@behance.net');
INSERT INTO customers (first_name, last_name, email) VALUES ('Zahara', 'Broadway', 'zbroadway7@theglobeandmail.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Delcina', 'Arnout', 'darnout8@eventbrite.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Alidia', 'Abramchik', 'aabramchik9@eventbrite.com');

-- READ included during first boot

-- UPDATE
UPDATE customers SET last_name = 'Autin' WHERE first_name = 'Delora';
UPDATE customers SET email = 'zbroadway@cnn.com' WHERE first_name = 'Zahara' AND last_name = 'Broadway';

-- DELETE
DELETE customers WHERE email = 'aabramchik9@eventbrite.com';
DELETE customers WHERE email = 'darnout8@eventbrite.com';

-- = = = = = = = = = = = = =
-- server1.dbo.orders
-- = = = = = = = = = = = = =
-- Read
SELECT * FROM orders;

-- CREATE
INSERT INTO orders(order_date,purchaser,quantity,product_id) VALUES ('16-JAN-2022', 1005, 5, 103);

-- READ included during first boot

-- UPDATE
UPDATE orders SET order_date = '16-JAN-2029' WHERE purchaser = 1005;

-- DELETE
DELETE orders WHERE purchaser = 1005;

-- = = = = = = = = = = = = =
-- server1.dbo.products
-- = = = = = = = = = = = = =
-- Read
SELECT * FROM products;

-- CREATE
INSERT INTO products(name,description,weight) VALUES ('playstation','PlayStation 5',50);

-- READ included during first boot

-- UPDATE
UPDATE products SET weight = '69' WHERE name = 'playstation';

-- DELETE
DELETE products WHERE name = 'playstation';

-- = = = = = = = = = = = = = = = 
-- server1.dbo.products_on_hand
-- = = = = = = = = = = = = = = =
-- Read
SELECT * FROM products_on_hand;

-- CREATE
INSERT INTO products_on_hand VALUES (111,69);

-- READ included during first boot

-- UPDATE
UPDATE products_on_hand SET quantity = 669 WHERE product_id = 111;

-- DELETE
DELETE products_on_hand WHERE product_id = 111;