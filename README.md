# Read Me
## Table of Contents
1. [Requirements](#requirements)
2. [Quickstart](#quickstart)
3. [Entities](#entities)
4. [Scripts](#scripts)
	1. [Cloudflare](#cloudflare)
		1. [Domain Name](#domainNameScripts)
			1. [List Domain Names](#listDomainNames)
		2. [DNS Record](#dnsRecordScripts)
			1. [List DNS Records](#listDnsRecords)
			2. [Create DNS Record](#createDnsRecord)
			3. [Update DNS Record](#updateDnsRecord)
			4. [Delete DNS Record](#deleteDnsRecord)
		3. [Lockdown Rule](#lockdownRuleScripts)
			1. [List Lockdown Rules](#listLockdownRules)
			2. [Create Lockdown Rule](#createLockdownRule)
			3. [Update Lockdown Rule](#updateLockdownRule)
			4. [Delete Lockdown Rule](#deleteLockdownRule)
	2. [OVH/ OpenAPI](#openAPI)
	3. [Scaleway](#scaleway)
		1. [Bootscript](#bootscriptScripts)
			1. [List Bootscripts](#listScalewayBootscripts)
		2. [Public IP](#publicIpScripts)
			1. [List Public IPs](#listScalewayPublicIps)
			2. [Reserve Public IP](#reserveScalewayPublicIp)
		3. [Scaleway Server](#scalewayServerScripts)
			1. [List Servers](#listScalewayServers)
			2. [Create Server](#createScalewayServer)
			3. [Update Server](#updateScalewayServer)
			4. [Delete Server](#deleteScalewayServer)
			5. [Backup Server](#backupScalewayServer)
		4. [Security Groups](#scalewaySecurityGroupScripts)
			1. [List Security Groups](#listScalewaySecurityGroups)
			2. [Create Security Group](#createScalewaySecurityGroup)
			3. [Update Security Group](#updateScalewaySecurityGroup)
			4. [Delete Security Group](#deleteScalewaySecurityGroup)
		5. [Server Image](#serverImageScripts)
			1. [List Server Images](#listScalewayServerImages)
			2. [Create Server Image](#createScalewayServerImage)
			3. [Delete Server Image](#deleteScalewayServerImage)
		6. [Server Volume](#serverVolumeScripts)
			1. [List Server Volumes](#listScalewayServerVolumes)
			2. [Create Server Volume](#createScalewayServerVolume)
			3. [Update Server Volume](#updateScalewayServerVolume)
			4. [Delete Server Volume](#deleteScalewayServerVolume)


## Requirements<a name="requirements"></a>
### [mv-credentials](https://github.com/webdrone-fr/mv-credentials)
#### Cloudflare
- Token
#### OVH/ OpenAPI
- Token
	+ Token needs to refreshed (on a weekly basis?)
		* checked at each call with an additional day added to expiry date if ok
- SSH key
#### Scaleway
- Token

### [mv-infrastructure](https://github.com/webdrone-fr/mv-infrastructure)
#### Cloudflare
- Service Provider
	+ API Url
#### OVH/ OpenAPI
- Service Provider
	+ API Url
#### Scaleway
- Service Provider
	+ API Url
	+ Populate Provider fields (action btn)

## Quickstart<a name="quickstart"></a>
### Create Server
1. Launch Meveo container
2. Install mv-credential + mv-infrastructure modules from github
2. Credential
	1. Check / ensure Credential (Entity) for desired service provider is present
		- If OVH, need valid ssh token
			+ (Link to procedure to generate token)
		- If not present, create Credential with fields:
			+ API Url
			+ API token
			+ Username
			+ Authentication method
		- Cloudflare:
			+ Authentication type: Header Token(HEADER)
			+ Header key EL: Authorization
			+ Header value: Bearer #{entity.getToken()}
			+ Domain or IP: api.cloudflare.com/client/v4
			+ Username: infrastructure@webdrone.fr
		- OVH/ OpenAPI:
		- Scaleway:
			+ Authentication type: Header Token(HEADER)
			+ Header key EL: X-Auth-Token
			+ Header value: #{entity.getToken()}
			+ Domain or IP: api.scaleway.com
			+ Username: webdrone
3. Service Provider
	1. Check/ ensure Service Provider (Entity) for required service provider is present
		- If not, create Entity with fields:
			+ API Url
		 	+ Code
		 	+ Description
	2. Populate provider fields (Btn)
	3. Look at available server types, images (if required) and available Public IPs(Scaleway)
4. Server
	1. List servers (limited to names starting with "dev-" and "int"(integration))
	2. To create a server:
		1. From an image (recomended):
			1. Select an Image(get uuid)
			2. Create a new (Provider)Server Entity
				1. Fill fields:
					1. Instance name
					2. zone
					3. type
					4. Image(Entity)
					5. Provider(Entity)
				2. Save Entity
				3. Create Server (Btn)
		2. From scratch: (need to verify procedure)
			1. Create a local volume of appropriate size (link to procedure)
			2. Create a new (Provider)Server Entity
				1. Fill fields:
					1. Instance name
					2. zone
					3. type
					4. Root Volume with id of previously created volume (Entity)
					5. Provider(Entity)
					6. Additonal volumes(optional)(Entity)
					7. Bootscript (Entity)/ Boottype
				2. Save Entity
				3. Create Server (Btn)

### DNS Resolution
1. Domain Name
	1. Check/ ensure Domain Name(Entity) for required domain name is present
	2. If not present, create Domain Name (Entity) with fields:
		1. Domain name: webdrone.fr
		2. Tld: fr
		3. Registrar: Cloudflare (Service Provider Entity)
	3. List Dns Records (Btn)
	4. List Lockdown Rules(Btn)
2. Dns Record
	1. Create Dns Record (Entity)
	2. Fill in fields:
		1. Domain Name (Entity)
		2. Name (ends with domain)
		3. Type
		4. Value (IP Address)
		5. Time to live
			- Must be either 1 (Automatic) or between 60 & 86400
	3. Save Entity
	4. Create Dns Record (Btn)
3. Lockdown rule
	1. List Lockdown Rules (from domain)
	2. Create or update Lockdown Rule :(limited availability of concurrent active rules)
	3. Add Dns Record to Lockdown Rule
		1. Includes address and domain for server

## Entities (For terminology and restrictions)<a name="entities"></a>
### Bootscript
Bootscripts are a combination of a Kernel and of an initrd. They tell to the instance how to start and configure its starting process and settings.
### Dns Record
- attached to a domain name
### Domain Name
### Lockdown Rule
Associated to a domain name and concerns the different dns records of that domain name
- limit to max number of active rules, based on account type(TBC), current is 3
### OVH Server
- keyname
- server network (Entity)
- Authentication used :
- API Url: SERVICE.ZONE.cloud.ovh.net/VERSION/
### Public IP
Independent IP that can be allocated to/ removed from a server/ passed from one server to another
### Scaleway Server
- Local volume size requirements must be met, depends on server type
- Ipv6 enabled (default is true)
- Dynamic IP (Ipv4) (default is true)
- project/ organization refer to the same entity (organisation is deprecated)
	+ Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
- Currently Unused:
	+ maintenances : planned maintenance to be performed on the server
	+ placement group: seperation of server instances
	+ private NICs= private networks server belongs to, additional functionality offered by scaleway
- Authentication used: X-Auth-Token in HEADER + TOKEN
- API Url: api.scaleway.com
- DEV SSH key should be preinstalled on the server: need to have a copy locally to connect to the server via ssh
### Security Group
A security group is a set of firewall rules on a set of instances. Security groups enable to create rules that either drop or allow incoming traffic from certain ports of your instances.
- Defines group for which security rules apply
### Security Rule
Rule to determine connection to/ from a server and intended behaviour
### Server
- Short term v long term availability requirements for server: long term better to use OVH as more economical
- Contains common fields accross servers from different providers
### Server Action
Action performed on a server
- Includes: poweron, poweroff, backup, stopinplace, terminate and reboot
- /tasks for check action status => not listed in documentation
### Server Image
Images are backups of your instances. You can reuse that image to restore your data or create a series of instances with a predefined configuration.

An image is a complete backup of your server including all volumes.
- Public vs private
### Server Network (OVH)
### Server User Data
User data is a key value store API you can use to provide data from and to your server without authentication.
### Server Volume
A volume is where you store your data inside your instance. It appears as a block device on Linux that you can use to create a filesystem and mount it.

- size displayed in bytes
- l_ssd (local) vs b_ssd (block)
### Service Provider

## Scripts<a name="scripts"></a>
### Cloudflare<a name="cloudflare"></a>
#### Domain Name<a name="domainNameScripts"></a>
##### List Domain Names<a name="listDomainNames"></a>
#### DNS Record<a name="dnsRecordScripts"></a>
##### List DNS Records for Domain<a name="listDnsRecords"></a>
##### Create DNS Record<a name="createDnsRecord"></a>
##### Update DNS Record<a name="updateDnsRecord"></a>
##### Delete DNS Record<a name="deleteDnsRecord"></a>
#### Lockdown Rule<a name="lockdownRuleScripts"></a>
##### List Lockdown rules for domain<a name="listLockdownRules"></a>
##### Create Lockdown Rule<a name="createLockdownRule"></a>
##### Update Lockdown Rule<a name="updateLockdownRule"></a>
##### Delete Lockdown Rule<a name="deleteLockdownRule"></a>

### OVH/ OpenAPI<a name="openAPI"></a>
OpenAPI documentation url: 
Version:

### Scaleway<a name="scaleway"></a>
- Scaleway Documentation url: https://developers.scaleway.com/en/products/instance/api/
- version: v1
#### Bootscripts<a name="scalewayBootscriptScripts"></a>
##### List Bootscripts<a name="listScalewayBootscripts"></a>
- Required fields:
- Restrictions/ Notes:
#### Public Ip<a name="scalewayPublicIpScripts"></a>
##### List public IPs<a name="listScalewayPublicIps"></a>
- Required fields:
- Restrictions/ Notes:
	+ will not refer to a server if server does not exist as an entity on meveo ie does not start with "dev-", see list of public ips in the service provider entity for full details
##### Reserve Public IP (not yet implemented and to be tested)<a name="reserveScalewayPublicIp"></a>
- Required fields:
- Restrictions/ Notes:

#### Server<a name="scalewayServerScripts"></a>
##### List Servers <a name="listScalewayServers"></a>
- Required fields:
- Restrictions/ Notes:
##### Create Server<a name="createScalewayServer"></a>
- Required fields:
	+ Name
	+ Provider 
	+ Zone
	+ Type
- Restrictions/ Notes:
###### From Image
- Get image id
- local volumes will be automatically created to meet size requirements for server type, additional block volumes can be added for further storage
###### Without Image
- Create volume
	+ Ensure meets size requirements for server type
- Bootscript
	+ Is part of image, can be allocated automatically or select from list
	+ requires boottype to be set to bootscript(default is local)

##### Update Server<a name="updateScalewayServer"></a>
- Required fields:
	+ Zone
	+ Server ID : Provider Side ID
	+ Provider (Entity)
	+ Security Group (Entity)
- Restrictions/ Notes:
	+ Cannot change the image of a server
	+ Cannot add/ remove a local volume to a running server
	+ Adding/ removing local volumes must meet size requirements for server type

##### Delete Server<a name="deleteScalewayServer"></a>
- Required fields:
- Restrictions/ Notes:
	+ Ensure Server is not running
	+ Terminate (not yet implemented)
	+ delete without removing volumes (option 1)
	+ delete as well as volumes (option 2)

##### Backup Server<a name="backupScalewayServer"></a>
- Required fields:
- Restrictions/ Notes:
	+ Creates an image + associated volumes
	+ Limited to the same zone as the original server (TBC)

#### Security group<a name="scalewaySecurityGroupScripts"></a>
##### List security groups<a name="listScalewaySecurityGroups"></a>
- Required fields:
- Restrictions/ Notes:
##### Create Security Group<a name="createScalewaySecurityGroup"></a>
- Required fields:
- Restrictions/ Notes:

##### Add Server to Security Group/ update security group<a name="updateScalewaySecurityGroup"></a>
- Required fields:
- Restrictions/ Notes:
##### List Security Group Rules<a name="listScalewaySecurityGroupRules"></a>
- Required fields:
- Restrictions/ Notes:
##### Create Security Group Rule<a name="createScalewaySecurityGroupRule"></a>
- Required fields:
- Restrictions/ Notes:
##### Update Security Group Rule<a name="updateScalewaySecurityGroupRule"></a>
- Required fields:
- Restrictions/ Notes:
##### Delete Scaleway Security Group Rule<a name="deleteScalewaySecurityGroupRule"></a>
- Required fields:
- Restrictions/ Notes:

#### Server Image<a name="scalewayServerImageScripts"></a>
##### List Server Images<a name="listScalewayServerImages"></a>
- Required fields:
- Restrictions/ Notes:
##### Create Server Image<a name="createScalewayServerImage"></a>
- Required fields:
- Restrictions/ Notes:
	+ from scratch (not yet implemented)
	+ from a server => backup(link)
##### Delete Server Image<a name="deleteScalewayServerImage"></a>
- Required fields:
- Restrictions/ Notes:

#### Server Volume<a name="scalewayServerVolumeScripts"></a>
##### List Server Volumes<a name="listScalewayServerVolumes"></a>
- Required fields:
- Restrictions/ Notes:
##### Create Server Volume<a name="createScalewayServerVolume"></a>
- Required fields:
	+ name
	+ size (in bytes)
	+ type (local or block)
	+ zone
- Restrictions/ Notes:
##### Update Server Volume<a name="updateScalewayServerVolume"></a>
- Required fields:
- Restrictions/ Notes:
	+ local volume can have name updated only
	+ block volume can have size and name updated
##### Delete Server Volume<a name="deleteScalewayServerVolume"></a>
- Required fields:
- Restrictions/ Notes:
	+ Check if volume is not attached to a server and that server is not running
