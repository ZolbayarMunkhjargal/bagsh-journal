-- Queries used by tools\read-msi.ps1 to describe an installer.
-- One query per line, read in this order. Table and column names that MSI treats as
-- SQL reserved words are quoted with backticks, which is why these live in a file
-- rather than being bashed out from PowerShell.
SELECT `Value` FROM `Property` WHERE `Property`='ProductName'
SELECT `Value` FROM `Property` WHERE `Property`='ProductVersion'
SELECT `Value` FROM `Property` WHERE `Property`='Manufacturer'
SELECT `Value` FROM `Property` WHERE `Property`='UpgradeCode'
SELECT `Value` FROM `Property` WHERE `Property`='ALLUSERS'
SELECT `Name` FROM `Shortcut`
SELECT `DefaultDir` FROM `Directory` WHERE `DefaultDir` LIKE '%KinderJournal%'
SELECT `DefaultDir` FROM `Directory` WHERE `DefaultDir` LIKE '%Menu%'
