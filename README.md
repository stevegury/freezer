# What is Freezer

Freezer is an incremental backup command-line tool for Amazon Glacier (à la git).

Amazon Glacier is one of the cheapest solution for backup files (~$1 for 100GB/month), but Amazon
doesn't provide an incremental backup tool. This is why I created this one.


# How to compile

	$ sbt assembly

It generates a big jar file in the target directory


# How to use

## Create a Amazon AWS account

Go to [Amazon AWS website](http://aws.amazon.com/) and create an account, then save your
credentials to a property file.
The default location freezer use is `~/.aws.glacier.credentials`

## Initializing a directory as a backup

	$ mkdir myproject
	$ cd myproject
	$ ... create files ...
	$ fz init
	VaultName [myproject]: <return>
	Credential location [/Users/username/.aws.glacier.credentials]: <return>
	Endpoint [https://glacier.us-east-1.amazonaws.com/]: <return>
	Exclusions (comma separated) []: \.git.*,.DS_Store
	$

This will create a subdirectory `.freezer` with metadata files.

## Backuping a directory

	$ fz backup
	Uploading new file: toto.txt
	Uploading new file: src/toto/titi.rs
	Uploading new file: src/toto/tutu.rs

The backup is incremental, only the new/modified/deleted files will be updated

	$ fz backup
	Updating modified file: src/toto/tutu.rs
	Removing deleted file: toto.txt
	$ fz backup
	Everything up-to-date.

## Requesting an Inventory

	$ fz inventory
	Inventory in progress (JobID: 'axx9MARjzksM6cG0QAbPsPLQlcah5M')

Amazon Glacier usually takes about 4 hours to complete an inventory, so be patient.

	$ fz inventory
	src/toto/titi.rs
	src/toto/tutu.rs

## Restoring an backup

Restoring a backup happens in two steps. The first one is requesting and retreiving an inventory
 (this usually takes a few hours), the second step is requesting an archive retrieval for every
 file in the inventory (this also takes a few hours).

	$ fz restore myproject
	Inventory in progress (JobID: 'axx9MARjzksM6cG0QAbPsPLQlcah5M')

Later...

	$ fz restore myproject
	Requesting download for: 'src/toto/titi.rs'
	Requesting download for: 'src/toto/tutu.rs'

Later...

	$ fz restore myproject
	Server still preparing: 'src/toto/titi.rs'
	Server still preparing: 'src/toto/tutu.rs'

You didn't wait long enough, what about now?

	$ fz restore myproject
	Downloading 'src/toto/titi.rs'
	Downloading 'src/toto/tutu.rs'

Finally!

