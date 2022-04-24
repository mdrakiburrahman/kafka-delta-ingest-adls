USE testDB
GO

-- CDC tables
SELECT * FROM cdc.change_tables
SELECT * FROM cdc.dbo_customers_CT
-- Data Tables
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
UPDATE customers SET last_name = 'Austin' WHERE first_name = 'Delora';
UPDATE customers SET email = 'zbroadway@cnn.com' WHERE first_name = 'Zahara' AND last_name = 'Broadway';

-- DELETE
DELETE customers WHERE email = 'aabramchik9@eventbrite.com';
DELETE customers WHERE email = 'darnout8@eventbrite.com';

-- Benchmark INSERTs
DROP PROCEDURE IF EXISTS dbo.RunInserts
GO

CREATE PROCEDURE dbo.RunInserts @Number int
AS
BEGIN
	DECLARE
		@Counter int= 1
	WHILE @Counter< =@Number
	BEGIN
		INSERT INTO customers(first_name,last_name,email)
		VALUES ('Raki','Rahman', CONCAT(NEWID (), '@microsoft.com'));
		PRINT(@Counter)
		SET @Counter= @Counter + 1
	END
END

EXEC dbo.RunInserts 100 -- <-- Tune as necessary
SELECT COUNT(*) AS num_rows FROM customers;

-- Restart Job to show idempotency
INSERT INTO customers (first_name, last_name, email) VALUES ('Alidia', 'Abramchik', 'aabramchik9@eventbrite.com');
INSERT INTO customers (first_name, last_name, email) VALUES ('Delcina', 'Arnout', 'darnout8@eventbrite.com');