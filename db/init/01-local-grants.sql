-- LAPTOP ONLY. The mysql image conjures `appuser` out of MYSQL_USER/MYSQL_PASSWORD, but it
-- grants privileges on MYSQL_DATABASE alone — so without this line the sidecar's own schema
-- would exist and still be unreachable.
GRANT ALL PRIVILEGES ON *.* TO 'appuser'@'%';
FLUSH PRIVILEGES;
