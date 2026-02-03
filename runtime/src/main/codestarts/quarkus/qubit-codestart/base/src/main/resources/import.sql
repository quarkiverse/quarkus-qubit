-- Sample data for Qubit codestart
INSERT INTO Person(id, name, age, active) VALUES (1, 'John Doe', 28, true);
INSERT INTO Person(id, name, age, active) VALUES (2, 'Jane Smith', 35, true);
INSERT INTO Person(id, name, age, active) VALUES (3, 'Bob Johnson', 17, true);
INSERT INTO Person(id, name, age, active) VALUES (4, 'Alice Brown', 42, false);
INSERT INTO Person(id, name, age, active) VALUES (5, 'Charlie Wilson', 22, true);

-- Update the sequence
ALTER SEQUENCE Person_SEQ RESTART WITH 6;
