
-- these PRAGMA's will automatically commit, so we have to disable
-- DH's automatic transactions, otherwise the connection will throw an error

--No Transactions--

pragma journal_mode = WAL;
pragma synchronous = NORMAL;
