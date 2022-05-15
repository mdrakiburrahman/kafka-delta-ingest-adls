-- Drop if exists
USE [master]
GO
ALTER DATABASE [AdventureWorks2019] SET SINGLE_USER WITH ROLLBACK IMMEDIATE
GO
PRINT '--> Dropping AdventureWorks2019 database if exists...'
DROP DATABASE IF EXISTS AdventureWorks2019;

-- Restore from .bak
PRINT '--> Restore AdventureWorks2019 from .bak...'
RESTORE DATABASE AdventureWorks2019 FROM  DISK = N'/var/opt/mssql/data/AdventureWorks2019.bak' WITH MOVE 'AdventureWorks2017' TO '/var/opt/mssql/data/AdventureWorks2019.mdf', MOVE 'AdventureWorks2017_Log' TO '/var/opt/mssql/data/AdventureWorks2019_Log.ldf'
GO

USE AdventureWorks2019;

-- Create Stored Proc to enable CDC on all tables
DROP PROCEDURE IF EXISTS sp_enable_disable_cdc_all_tables
GO

PRINT '--> Creating Stored Proc to enable CDC on all tables...'
GO

CREATE PROCEDURE sp_enable_disable_cdc_all_tables(@dbname varchar(100), @enable bit)  
AS  
  
BEGIN TRY  

DECLARE @table_schema varchar(400);  
DECLARE @table_name varchar(400);  
DECLARE @sql varchar(1000)  

DECLARE the_cursor CURSOR FAST_FORWARD FOR  

SELECT TABLE_SCHEMA, TABLE_NAME   
FROM INFORMATION_SCHEMA.TABLES where TABLE_CATALOG=@dbname and TABLE_NAME != 'systranschemas' and TABLE_SCHEMA!='dbo' and TABLE_TYPE='BASE TABLE' and TABLE_SCHEMA!='cdc'

OPEN the_cursor  
FETCH NEXT FROM the_cursor INTO @table_schema, @table_name
  
WHILE @@FETCH_STATUS = 0  
BEGIN  
if @enable = 1  
  
set @sql =' Use '+ @dbname+ ';EXEC sys.sp_cdc_enable_table  
            @source_schema = '+@table_schema+',@source_name = '+@table_name+'  
          , @role_name = NULL, @supports_net_changes = 0;'
            
else  
set @sql =' Use '+ @dbname+ ';EXEC sys.sp_cdc_disable_table  
            @source_schema = '+@table_schema+',@source_name = '+@table_name+',  @capture_instance =''all'''  
exec(@sql)  
  
  FETCH NEXT FROM the_cursor INTO @table_schema, @table_name
  
END  
  
CLOSE the_cursor  
DEALLOCATE the_cursor  
  
      
SELECT 'Successful'  
END TRY  
BEGIN CATCH  
CLOSE the_cursor  
DEALLOCATE the_cursor  
  
    SELECT   
        ERROR_NUMBER() AS ErrorNumber  
        ,ERROR_MESSAGE() AS ErrorMessage;  
END CATCH
GO

PRINT '--> Created Stored Proc successfully!'

-- Run sp_enable_disable_cdc_all_tables
PRINT '--> Enabling CDC on all tables...'
EXEC sp_changedbowner 'sa';
EXEC sys.sp_cdc_enable_db;
EXEC sp_enable_disable_cdc_all_tables 'AdventureWorks2019', 1;
PRINT '--> Enabled CDC on all tables successfully!'