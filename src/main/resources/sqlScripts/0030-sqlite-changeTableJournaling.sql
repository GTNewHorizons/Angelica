
-- this PRAGMA will automatically commit, so we have to disable
-- DH's automatic transactions, otherwise the connection will throw an error

--No Transactions--

-- James ran into some issues where Windows had trouble deleting the Journal file, 
-- using TRUNCATE should fix that issue
PRAGMA journal_mode = TRUNCATE;
