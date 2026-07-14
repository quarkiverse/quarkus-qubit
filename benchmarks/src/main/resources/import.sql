-- Qubit Integration Tests - Test Data
-- This file provides comprehensive test data for native integration tests.
-- Data matches the patterns defined in TestDataFactory.java

-- ============================================================
-- DEPARTMENTS (must be created first for foreign key references)
-- ============================================================
INSERT INTO Department (id, name, code, budget) VALUES (1, 'Engineering', 'ENG', 500000);
INSERT INTO Department (id, name, code, budget) VALUES (2, 'Sales', 'SLS', 300000);
INSERT INTO Department (id, name, code, budget) VALUES (3, 'Human Resources', 'HR', 150000);
INSERT INTO Department (id, name, code, budget) VALUES (4, 'Marketing', 'MKT', 200000);

-- Update sequence for PostgreSQL
ALTER SEQUENCE department_seq RESTART WITH 100;

-- ============================================================
-- PERSONS (Standard test set matching TestDataFactory)
-- ============================================================
-- John Doe - 30, active, Engineering, salary 75000
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (1, 'John', 'Doe', 'john.doe@example.com', 30, '1993-05-15', true, 75000.0, 1000001, 1.75, '2024-01-15 09:30:00', '09:00:00', 1);

-- Jane Smith - 25, active, Sales, salary 65000
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (2, 'Jane', 'Smith', 'jane.smith@example.com', 25, '1998-08-22', true, 65000.0, 1000002, 1.68, '2024-02-20 14:45:00', '08:30:00', 2);

-- Bob Johnson - 45, inactive, HR, salary 85000
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (3, 'Bob', 'Johnson', 'bob.johnson@example.com', 45, '1978-03-10', false, 85000.0, 1000003, 1.82, '2024-03-10 08:00:00', '10:00:00', 3);

-- Alice Williams - 35, active, Engineering, salary 90000
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (4, 'Alice', 'Williams', 'alice.williams@example.com', 35, '1988-11-05', true, 90000.0, 1000004, 1.65, '2024-04-05 16:20:00', '08:00:00', 1);

-- Charlie Brown - 28, active, Sales, salary 55000
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (5, 'Charlie', 'Brown', 'charlie.brown@example.com', 28, '1995-07-18', true, 55000.0, 1000005, 1.78, '2024-05-12 10:15:00', '09:30:00', 2);

-- David Miller - 40, active, Marketing, salary 95000 (for additional tests)
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (6, 'David', 'Miller', 'david.miller@example.com', 40, '1984-06-20', true, 95000.0, 1000006, 1.80, '2024-06-01 11:00:00', '09:15:00', 4);

-- David Miller with spaces (for string trimming tests)
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (7, 'David', 'Miller', ' david.miller@example.com ', 40, '1984-06-20', true, 95000.0, 1000007, 1.80, '2024-06-01 11:00:00', '09:15:00', 4);

-- Eve Wilson - with nulls (for null checking tests)
INSERT INTO Person (id, firstName, lastName, email, age, birthDate, active, salary, employeeId, height, createdAt, startTime, department_id)
VALUES (8, 'Eve', 'Wilson', '', 50, NULL, false, NULL, NULL, NULL, NULL, NULL, NULL);

-- Update sequence for PostgreSQL
ALTER SEQUENCE person_seq RESTART WITH 100;

-- ============================================================
-- PHONES (One-to-Many relationship with Person)
-- ============================================================
-- John's phones (2 phones)
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (1, '555-0101', 'mobile', true, 1);
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (2, '555-0102', 'work', false, 1);

-- Jane's phones (1 phone)
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (3, '555-0201', 'mobile', true, 2);

-- Bob's phones (3 phones)
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (4, '555-0301', 'mobile', true, 3);
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (5, '555-0302', 'home', false, 3);
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (6, '555-0303', 'work', false, 3);

-- Alice's phones (2 phones)
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (7, '555-0401', 'mobile', true, 4);
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (8, '555-0402', 'work', false, 4);

-- Charlie's phones (1 phone)
INSERT INTO Phone (id, number, type, isPrimaryPhone, owner_id) VALUES (9, '555-0501', 'mobile', true, 5);

-- Update sequence for PostgreSQL
ALTER SEQUENCE phone_seq RESTART WITH 100;

-- ============================================================
-- TAGS (for Many-to-Many with Products)
-- ============================================================
INSERT INTO Tag (id, name, color) VALUES (1, 'electronics', 'blue');
INSERT INTO Tag (id, name, color) VALUES (2, 'premium', 'gold');
INSERT INTO Tag (id, name, color) VALUES (3, 'bestseller', 'green');
INSERT INTO Tag (id, name, color) VALUES (4, 'sale', 'red');
INSERT INTO Tag (id, name, color) VALUES (5, 'new-arrival', 'purple');
INSERT INTO Tag (id, name, color) VALUES (6, 'eco-friendly', 'lime');

-- Update sequence for PostgreSQL
ALTER SEQUENCE tag_seq RESTART WITH 100;

-- ============================================================
-- PRODUCTS (Standard test set matching TestDataFactory)
-- ============================================================
INSERT INTO Product (id, name, category, price, stockQuantity, available, description, rating)
VALUES (1, 'Laptop', 'Electronics', 1299.99, 50, true, 'High-performance laptop', 4.5);

INSERT INTO Product (id, name, category, price, stockQuantity, available, description, rating)
VALUES (2, 'Smartphone', 'Electronics', 899.99, 100, true, 'Latest smartphone model', 4.7);

INSERT INTO Product (id, name, category, price, stockQuantity, available, description, rating)
VALUES (3, 'Desk Chair', 'Furniture', 299.99, 25, true, 'Ergonomic office chair', 4.2);

INSERT INTO Product (id, name, category, price, stockQuantity, available, description, rating)
VALUES (4, 'Coffee Maker', 'Appliances', 89.99, 0, false, 'Programmable coffee maker', 4.0);

INSERT INTO Product (id, name, category, price, stockQuantity, available, description, rating)
VALUES (5, 'Monitor', 'Electronics', 399.99, 30, true, '27-inch 4K monitor', 4.6);

-- Update sequence for PostgreSQL
ALTER SEQUENCE product_seq RESTART WITH 100;

-- ============================================================
-- PRODUCT_TAG (Many-to-Many join table)
-- ============================================================
-- Laptop: electronics, premium, bestseller
INSERT INTO product_tag (product_id, tag_id) VALUES (1, 1);
INSERT INTO product_tag (product_id, tag_id) VALUES (1, 2);
INSERT INTO product_tag (product_id, tag_id) VALUES (1, 3);

-- Smartphone: electronics, new-arrival, bestseller
INSERT INTO product_tag (product_id, tag_id) VALUES (2, 1);
INSERT INTO product_tag (product_id, tag_id) VALUES (2, 5);
INSERT INTO product_tag (product_id, tag_id) VALUES (2, 3);

-- Desk Chair: eco-friendly, sale
INSERT INTO product_tag (product_id, tag_id) VALUES (3, 6);
INSERT INTO product_tag (product_id, tag_id) VALUES (3, 4);

-- Coffee Maker: sale
INSERT INTO product_tag (product_id, tag_id) VALUES (4, 4);

-- Monitor: electronics, premium
INSERT INTO product_tag (product_id, tag_id) VALUES (5, 1);
INSERT INTO product_tag (product_id, tag_id) VALUES (5, 2);

-- ============================================================
-- ANIMALS (SINGLE_TABLE inheritance: Animal -> Dog, Cat)
-- ============================================================
-- DTYPE column is auto-managed by Hibernate for discriminator
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (1, 'Dog', 'Rex', 30, true, 'Labrador', true, null, null);
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (2, 'Dog', 'Buddy', 25, true, 'Golden Retriever', false, null, null);
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (3, 'Dog', 'Max', 35, false, 'German Shepherd', true, null, null);
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (4, 'Cat', 'Whiskers', 5, true, null, null, true, 'black');
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (5, 'Cat', 'Luna', 4, true, null, null, false, 'white');
INSERT INTO Animal (id, DTYPE, name, weight, vaccinated, breed, trained, indoor, color) VALUES (6, 'Cat', 'Mittens', 6, false, null, null, true, 'orange');

-- Update sequence for PostgreSQL
ALTER SEQUENCE animal_seq RESTART WITH 100;
