# Read Me
## Table of Contents
1. [Requirements](#requirements)
2. [Quickstart](#quickstart)
	1. [Create Server](#createServerQuickstart)
	2. [DNS Resolution](#dnsResolutionQuickstart)
3. [Entities](#entities)
4. [Scripts](#scripts)
	1. [Cloudflare](#cloudflare)
		1. [Cloudflare Token](#cloudflareTokenScripts)
			1. [Check Cloudflare Token Validity](#checkCloudflareTokenValidity)
		2. [Domain Name](#domainNameScripts)
			1. [List Domain Names](#listDomainNames)
		3. [DNS Record](#dnsRecordScripts)
			1. [List DNS Records](#listDnsRecords)
			2. [Create DNS Record](#createDnsRecord)
			3. [Update DNS Record](#updateDnsRecord)
			4. [Delete DNS Record](#deleteDnsRecord)
		4. [Lockdown Rule](#lockdownRuleScripts)
			1. [List Lockdown Rules](#listLockdownRules)
			2. [Create Lockdown Rule](#createLockdownRule)
			3. [Update Lockdown Rule](#updateLockdownRule)
			4. [Delete Lockdown Rule](#deleteLockdownRule)
	2. [OVH/ OpenStack](#openStack)
		1. [OVH Token](#openStackTokenScripts)
			1. [Check OVH Token](#checkOpenStackToken)
		2. [Server](#openStackServerScripts)
			1. [List Servers](#listOVHServers)
			2. [Create Server](#createOVHServer)
			3. [Update Server](#updateOVHServer)
			4. [Delete Server](#deleteOVHServer)
		3. [API](#openStackAPIScripts)
			1. [Block Storage](#blockStorageOpenStackAPI)
			2. [Compute](#computeOpenStackAPI)
			3. [Identity](#identityOpenStackAPI)
			4. [Image](#imageOpenStackAPI)
			5. [Network](#networkOpenStackAPI)
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
#### OVH/ OpenStack
- Token
	+ Token needs to refreshed (on a weekly basis?)
		* checked at each call with an additional day added to expiry date if ok
- SSH key
#### Scaleway
- Token

### [mv-infrastructure](https://github.com/webdrone-fr/mv-infrastructure)
#### Cloudflare
- Service Provider
	+ Authentication type: Header Token(HEADER)
	+ Header key EL: Authorization
	+ Header value: Bearer #{entity.getToken()}
	+ Domain or IP: api.cloudflare.com/client/v4
	+ Username: infrastructure@webdrone.fr
#### OVH/ OpenStack 
- Service Provider
	+ API Url
#### Scaleway
- Service Provider
	+ Authentication type: Header Token(HEADER)
	+ Header key EL: X-Auth-Token
	+ Header value: #{entity.getToken()}
	+ Domain or IP: api.scaleway.com
	+ Username: webdrone
	+ Populate Provider fields (Btn)

## Quickstart<a name="quickstart"></a>
### Create Server<a name="createServerQuickstart"></a>
1. Launch Meveo container
2. Install mv-credential + mv-infrastructure modules from github
	- [mv-credentials](https://github.com/webdrone-fr/mv-credentials)
	- [mv-infrastructure](https://github.com/webdrone-fr/mv-infrastructure)
2. Credential
	1. Check / ensure Credential (Entity) for desired Service Provider (Entity) is present
		- If OVH, need valid ssh token
			+ (Link to procedure to generate token) Or is only from INFRA?
		- If not present, create Credential with fields:
			+ API Url
			+ API token
			+ Username
			+ Authentication method
3. Service Provider
	1. Check/ ensure Service Provider (Entity) required is present
		- If not, create Service Provider (Entity) with fields:
			+ API Url
		 	+ Code
		 	+ Description
	2. Populate provider fields (Btn)
	3. Look at available Server Types, Images (if required) and available Public IPs
4. Server
	1. List Servers (limited to names starting with "dev-" and "int"(integration))
	2. To create a Server:
		1. From an Image (recomended):
			1. Select an Image(copy uuid)
			2. Create a new (ProviderName)Server Entity
				1. Required fields:
					1. Instance name
					2. Zone
					3. Type
					4. Image (Entity)
					5. Service Provider (Entity)
				2. Save Entity
				3. Create Server (Btn)
		2. From scratch: (need to verify procedure)
			1. [Create a local Volume](#createScalewayServerVolume) (l_ssd) of appropriate size
			2. Create a new (ProviderName)Server Entity
				1. Fill fields:
					1. Instance name
					2. Zone
					3. Type
					4. Root Volume with id of previously created Volume (Entity)
					5. Service Provider (Entity)
					6. Additonal Volumes (Optional)(Entity)
					7. Bootscript (Entity)
						1. Set boot type to bootscript
				2. Save Entity
				3. Create Server (Btn)

### DNS Resolution<a name="dnsResolutionQuickstart"></a>
1. Domain Name
	1. Check/ ensure Domain Name (Entity) required is present
	2. If not present, create Domain Name (Entity) with fields:
		1. Domain name: webdrone.fr
		2. Tld: fr
		3. Registrar: Cloudflare (Service Provider Entity)
	3. List DNS Records (Btn)
	4. List Lockdown Rules(Btn)
2. Dns Record
	1. Create DNS Record (Entity)
	2. Fill in fields:
		1. Domain Name (Entity)
		2. Name (ends with domain, ie .webdrone.fr)
		3. Type
		4. Value (IP address of Server)
		5. Time to live
			- Must be either 1 (Automatic) or between 60 & 86400
	3. Save Entity
	4. Create DNS Record (Btn)
3. Lockdown rule
	1. List Lockdown Rules (from Domain)
	2. Create or update Lockdown Rule
		- limited availability of concurrently active rules
	3. Add DNS Record to Lockdown Rule
		1. Includes IP address and domain for Server

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

### Public IP
A flexible IP address is an IP address which is held independently of any server. It can be attached to any server and do live migrations of the IP address between servers.
Be aware that attaching a flexible IP address to a server will remove the previous public IP address of the server and cut any ongoing public connection to the server.
### Scaleway Server
Scaleway offers Virtual Cloud and dedicated GPU instances.Virtual cloud instances are offering the best performance/price ratio for most workloads.
 - The Development instances range provides stable and consistent performance for development needs.
- The General Purpose instances range is the solution for demanding workloads.
GPU instances are very powerful compute instances, providing lots of RAM, vCPU, and storage.
- They are useful for heavy data processing, artificial intelligence and machine learning, video encoding, rendering, and so on.
### Security Group
A security group is a set of firewall rules on a set of instances. Security groups enable to create rules that either drop or allow incoming traffic from certain ports of your instances.
### Security Rule
Rule to determine connection to/ from a server and intended behaviour
### Server
- Short term v long term availability requirements for server: long term better to use OVH as more economical
- Contains common fields accross servers from different providers
### Server Action
Action performed on a Server
- Includes: poweron, poweroff, backup, stopinplace, terminate and reboot
### Server Image
Images are backups of your instances. You can reuse that Image to restore your data or create a series of instances with a predefined configuration.
An image is a complete backup of your server including all volumes.
### Server Network (OVH)
Address range from which a Server instance will be allocated a public IP.
### Server User Data
User data is a key value store API you can use to provide data from and to your server without authentication.
### Server Volume
A volume is where you store your data inside your instance. It appears as a block device on Linux that you can use to create a filesystem and mount it.
### Service Provider
External Provider that manages infrastructure (IaaS) including servers and domains.

## Scripts<a name="scripts"></a>
### Cloudflare<a name="cloudflare"></a>
#### Cloudflare Token<a name="cloudflareTokenScripts"></a>
##### Check Cloudflare Token Validity<a name="checkCloudflareTokenValidity"></a>
- Required fields:
	+ Credential (Entity) for Cloudflare
- Process:
	+ Start from Domain Name (Entity)
	+ Check Cloudflare Token Validity (Btn)
- Restrictions/ Notes:
	+ Result will be displayed as popup
	+ (Need to add a block if invalid)
#### Domain Name<a name="domainNameScripts"></a>
##### List Domain Names<a name="listDomainNames"></a>
- Required fields:
	+ Credential (Entity) for Cloudflare
- Process:
	+ Option 1
		* Start from Domain Name Entity List
		* List Cloudflare Domains (Btn)
	+ Option 2
		* From Service Provider (Entity)
		* List Provicer Domains (Btn) (Need to add)
- Restrictions/ Notes:

#### DNS Record<a name="dnsRecordScripts"></a>
- Max length of Name is 255 characters
- Type: 
	+ Valid values: A, AAAA, CNAME, HTTPS, TXT, SRV, LOC, MX, NS, CERT, DNSKEY, DS, NAPTR, SMIMEA, SSHFP, SVCB, TLSA, URI
	+ Read only
- Time to live must be either 1 (Automatic) or between 60 & 86400
- Priority (Optional):
	+ Required for MX, SRV and URI records
	+ Records with lower priorities are preferred
- Proxied (Optional):
	+ Whether the record is receiving the performance and security benefits of Cloudflare
##### List DNS Records for Domain<a name="listDnsRecords"></a>
- Required fields:
	+ Domain Name (Entity)
- Process:
	+ Start from Domain Name (Entity)
	+ List Cloudflare DNS Records (Btn)
- Restrictions/ Notes:

##### Create DNS Record<a name="createDnsRecord"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Type
	+ Name
	+ Value (IP address of Server)
	+ Time to live
- Process:
	+ Start from DNS Records Entity List
	+ New (Btn)
	+ Fill in required fields
	+ Save Entity
	+ Create Cloudflare DNS Record (Btn)
- Restrictions/ Notes:

##### Update DNS Record<a name="updateDnsRecord"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Provider Side ID
	+ Type
	+ Name
	+ Value
	+ Time to live
	+ Proxied (Optional)
- Process:
	+ Start from DNS Record (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Cloudflare DNS Record (Btn)
- Restrictions/ Notes:

##### Delete DNS Record<a name="deleteDnsRecord"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Provider Side ID
- Process:
	+ Start from DNS Record (Entity)
	+ Delete Cloudflare DNS Record (Btn)
- Restrictions/ Notes:

#### Lockdown Rule<a name="lockdownRuleScripts"></a>
- Description max length is 1024 characters
##### List Lockdown rules for domain<a name="listLockdownRules"></a>
- Required fields:
	+ Domain Name (Entity)
- Process:
	+ Start from Domain Name (Entity)
	+ List Cloudflare Lockdown Rules (Btn)
- Restrictions/ Notes:

##### Create Lockdown Rule<a name="createLockdownRule"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Urls (min 1)
	+ IPs and/ or IP ranges (min 1)
	+ Description (Optional)
	+ Paused (Optional)
- Process:
	+ Start from Lockdown Rule Entity List
	+ New (Btn)
	+ Fill in required fields
	+ Save Entity
	+ Create Cloudflare Lockdown Rule (Btn)
- Restrictions/ Notes:

##### Update Lockdown Rule<a name="updateLockdownRule"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Provider Side ID
	+ Urls (min 1)
	+ IPs and/ or IP ranges (min 1)
	+ Description (Optional)
	+ Paused (Optional)
- Process:
	+ Start from Lockdown Rule (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Cloudflare Lockdown Rule (Btn)
- Restrictions/ Notes:

##### Delete Lockdown Rule<a name="deleteLockdownRule"></a>
- Required fields:
	+ Domain Name (Entity)
	+ Provider Side ID
- Process:
	+ Start from Lockdown Rule (Entity)
	+ Delete Cloudflare Lockdown Rule (Btn)
- Restrictions/ Notes:

### OVH/ OpenStack<a name="openStack"></a>
- keyname
- Server Network (Entity)
- Authentication used : User/Password and/or token
- API Url: SERVICE.ZONE.cloud.ovh.net/VERSION/
#### OVH Token<a name="openStackTokenScripts"></a>
##### Check OVH Token Validity<a name="checkOpenStackToken"></a>
- Required fields:
	+ Credential of the openstack account 
- Process:
	+ Start from Credentials
	+ OVH Check Token (Btn)
- Restrictions/ Notes:
#### Server<a name="openStackServerScripts"></a>
##### List OVH Servers<a name="listOVHServers"></a>
- Required fields:
	+ A valid openstack token
- Process:
	+ Server provider (CET)
	+ OVH (entity)
	+ List Servers (Btn)
- Restrictions/ Notes:
##### Create OVH Server<a name="createOVHServer"></a>
- Required fields:
	+ A valid openstack token
	+ Server OVH (Entity)
	+ Instance Name (Field)
	+ Server Type (Entity)
	+ Image (Entity)
	+ Ssh Key Name (Field)
	+ Network (Entity)
- Process:
	+ Fill all required field
	+ Return to the listing of Server entity
	+ Go back again on the entity
	+ Create Server (Btn)
- Restrictions/ Notes:
##### Update OVH Server<a name="updateOVHServer"></a>
- Required fields:
- Process:
	+ Update the field you want (see Restrictions )
- Restrictions/ Notes:
	+ Used the update field of an existing server
	+ Can't change the provider if the server is created
	+ Instance name cannot be empty
	+ No restriction for organization
	+ No restriction for Security Group entity
	+ Ssh Key Name cannot be empty
	+ Network cannot be empty
##### Delete OVH Server<a name="deleteOVHServer"></a>
- Required fields:
- Process:
	+ Delete Server (Btn)
- Restrictions/ Notes:
	+ The server will be delete without any backup
#### API<a name="openStackAPIScripts"></a>
##### Block Storage<a name="blockStorageOpenStackAPI"></a>
- Required fields:
	+ Option 1 - Create Backup :
		* Project id
		* Volume id
		* Name
	+ Option 2 - Restore Backup :
		* Project id
		* Backup id
	+ Option 3 - Restore Backup :
		* Project id
		* Backup id
- Process:
- Restrictions/ Notes:
	+ Save data of the server, can be restored an another server
	+ OpenStack documentation url: https://docs.openstack.org/api-ref/block-storage/v3/
	+ Version: 3.59 (v3.0)
##### Compute<a name="computeOpenStackAPI"></a>
- Required fields:
- Process:
- Restrictions/ Notes:
	+ OpenStack documentation url: https://docs.openstack.org/api-ref/compute/
	+ Version: 3.14 (Usuri)
##### Identity<a name="identityOpenStackAPI"></a>
- Required fields:
- Process:
- Restrictions/ Notes:
	+ OpenStack documentation url: https://docs.openstack.org/api-ref/identity/v3/
	+ Version: 2.38 (2.1)
##### Image<a name="imageOpenStackAPI"></a>
- Required fields:
- Process:
- Restrictions/ Notes:
	+ OpenStack documentation url: https://docs.openstack.org/api-ref/image/v2/index.html
	+ Version: 2.4 (v2.4)
##### Network<a name="networkOpenStackAPI"></a>
- Required fields:
- Process:
- Restrictions/ Notes:
	+ OpenStack documentation url: https://docs.openstack.org/api-ref/network/v2/index.html
	+ Version: 2.0 (2.0)

### Scaleway<a name="scaleway"></a>
- Scaleway Documentation url: https://developers.scaleway.com/en/products/instance/api/
- version: v1
- Authentication used: X-Auth-Token in HEADER + TOKEN
- API Url: api.scaleway.com
- DEV ssh key should be preinstalled on the server: need to have a copy locally to connect to the server via ssh
#### Bootscripts<a name="scalewayBootscriptScripts"></a>
##### List Bootscripts<a name="listScalewayBootscripts"></a>
- Required fields:
	+ Credential (Entity) for Scaleway
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Option 1
		* Start from Bootscript Entity List
		* List Scaleway Bootscripts (Btn)
	+ Option 2
		* Start from Scaleway Service Provider (Entity)
		* Populate Provider Fields (Btn)
			- Will be limited to those currently in use (TO BE CHANGED)
- Restrictions/ Notes:

#### Public IP<a name="scalewayPublicIpScripts"></a>
##### List Public IPs<a name="listScalewayPublicIps"></a>
- Required fields:
	+ Credential (Entity) for Scaleway
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Start from Scaleway Service Provider (Entity)
	+ Populate Provider Fields (Btn)
- Restrictions/ Notes:
	+ Entity will not refer to a Server (Entity) if Server does not exist as an entity on meveo ie does not start with "dev-" or "int", see list of Public IPs in the Scaleway Service Provider (Entity) for full list of Servers
##### Reserve Public IP (not yet implemented and to be tested)<a name="reserveScalewayPublicIp"></a>
- Required fields:
	+ Credential (Entity) for Scaleway
	+ Service Provider (Entity) for Scaleway
- Process:
- Restrictions/ Notes:

#### Server<a name="scalewayServerScripts"></a>
- Local volume size requirements must be met, depends on server type
- Ipv6 enabled (default is true)
- Dynamic IP (IPv4) (default is true)
- Project/ Organization refer to the same entity (Organisation is deprecated)
	+ Webdrone ID : 6a0c2ca8-917a-418a-90a3-05949b55a7ae
- Currently Unused:
	+ Maintenances: planned maintenance to be performed on the server
	+ Placement group: seperation of server instances
	+ Private NICs: private networks server belongs to, additional functionality offered by Scaleway
- /tasks for check action status => not listed inSscaleway documentation
##### List Servers <a name="listScalewayServers"></a>
- Required fields:
	+ Credential (Entity) for Scaleway
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Start from Scaleway Server Entity List
	+ List Scaleway Servers (Btn)
- Restrictions/ Notes:

##### Create Server<a name="createScalewayServer"></a>
- Required fields:
	+ Option 1: From Image
		* Service Provider (Entity) for Scaleway
		* Name
		* Zone
		* Type
		* Image (Entity)
		* Security Group (Optional)
	+ Option 2: Without Image (To be tested still)
		* Service Provider (Entity) for Scaleway
		* Name
		* Zone
		* Type
		* Root Volume (Entity)
		* Additional Volumes (Optional) (Entity)
		* Security Group (Optional)
		* (Bootscript/ Boot Type) (To be tested)
- Process:
	+ Option 1: From Image (Entity)
		* Get Image ID
			- Local volumes will be automatically created to meet size requirements for server type, additional block volumes can be added for further storage
		* Start from Scaleway Server Entity List
		* New (Btn)
		* Fill in required fields for Option 1
		* Save Entity
		* Create Scaleway Server (Btn)
	+ Option 2: Without Image
		* [Create Volume](#createScalewayServerVolume) (Entity) 
			- Ensure  Volume size meets size requirements for Server type
			- Requirement can be found in Scaleway Service Provider (Entity) under Server Types
		* Bootscript
			- Can be allocated automatically or selected from list
			- Requires boot type to be set to bootscript(default is local)
		* Start from Scaleway Server Entity List
		* New (Btn)
		* Fill in fields required for Option 2
		* Save Entity
		* Create Scaleway Server (Btn)
- Restrictions/ Notes:

##### Update Server<a name="updateScalewayServer"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Security Group (Entity)
	+ Zone
	+ Provider Side ID
- Process:
	+ Start from Scaleway Server (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Scaleway Server (Btn)
- Restrictions/ Notes:
	+ Cannot change the Image of a Server
	+ Cannot add/ remove a local Volume of a running Server
	+ Adding/ removing local Volumes must meet size requirements for Server type

##### Delete Server<a name="deleteScalewayServer"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
	+ Zone
- Process:
	+ Option 1: Delete without removing volumes
		* Start from Scaleway Server (Entity)
		* Delete Scaleway Server (Btn)
			- No Volume should be attached to the Server
	+ Option 2: Delete as well as volumes
		* Terminate (not yet implemented)
		* Start from Scaleway Server (Entity)
		* Delete Scaleway Server with Volumes (Btn)
- Restrictions/ Notes:
	+ Ensure Server is not running

##### Backup Server<a name="backupScalewayServer"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
	+ Zone
	+ Backup Name (Optional)
- Process:
	+ Start from Scaleway Server (Entity)
	+ Fill in Backup Name (Optional)
	+ Backup Server (Btn)
- Restrictions/ Notes:
	+ Creates an Image + associated Volumes
	+ Limited to the same zone as the original Server (TBC)

#### Security Group<a name="scalewaySecurityGroupScripts"></a>
- Project default: 
	+ Whether this security group becomes the default security group for new instances.
	+ Default is false
- Stateful
	+ Whether the security group is stateful or not.
	+ Default is false
- Inbound default policy:
	+ Default policy for inbound rules.
	+ Possible values are accept and drop
	+ Default is accept
- Outbound default policy:
	+ Default policy for outbound rules
	+ Possible values are accept and drop
	+ Default is accept
##### List Security Groups<a name="listScalewaySecurityGroups"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Option 1
		* Start from Scaleway Service Provider (Entity)
		* Populate Provider Fields (Btn)
	+ Option 2
		* Start from Security Group Entity List
		* List Scaleway Security Groups (Btn)
- Restrictions/ Notes:

##### Create Security Group<a name="createScalewaySecurityGroup"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Name
	+ Zone
- Process:
	+ Start from Security Group Entity List
	+ New (Btn)
	+ Fill in required fields
	+ Save Entity
	+ Create Scaleway Security Group (Btn)
- Restrictions/ Notes:

##### Add Server to Security Group/ Update Security Group<a name="updateScalewaySecurityGroup"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
	+ Zone
	+ Name
	+ Servers (Optional)
	+ Description (Optional)
- Process:
	+ Start from Security Group (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Security Group (Btn) (To be added)
- Restrictions/ Notes:

#### Security Group Rule<a name="scalewaySecurityGroupRuleScripts"></a>
- Protocol:
	+ Possible values are TCP, UDP, ICMP and ANY.
	+ Default is TCP
- Direction:
	+ Possible values are inbound and outbound.
	+ Default is inbound
- Action:
	+ Possible values are accept and drop.
	+ Default is accept
##### List Security Group Rules<a name="listScalewaySecurityGroupRules"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Security Group (Entity)
- Process:
	+ Start from Security Group (Entity)
	+ List Security Group Rules (Btn)
- Restrictions/ Notes:

##### Create Security Group Rule<a name="createScalewaySecurityGroupRule"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Security Group (Entity)
	+ Zone
	+ Protocol
	+ Direction
	+ Action
	+ IP Range
	+ Position
	+ Editable
	+ Destination Port From (Optional)
	+ Destination Port To (Optional)
- Process:
	+ Start from Security Group Rule Entity List
	+ New (Btn)
	+ Fill in required fields
	+ Save Entity
	+ Create Scaleway Security Group Rule (Btn)
- Restrictions/ Notes:

##### Update Security Group Rule<a name="updateScalewaySecurityGroupRule"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Security Group (Entity)
	+ Provider Side ID
- Process:
	+ Start from Security Group Rule (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Scaleway Security Group Rule (Btn)
- Restrictions/ Notes:

##### Delete Scaleway Security Group Rule<a name="deleteScalewaySecurityGroupRule"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Security Group (Entity)
	+ Provider Side ID
	+ Zone
- Process:
	+ Start from Security Group Rule (Entity)
	+ Delete Scaleway Security Group Rule (Btn)
- Restrictions/ Notes:

#### Server Image<a name="scalewayServerImageScripts"></a>
##### List Server Images<a name="listScalewayServerImages"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Option 1: From Service Provider
		* Start from Scaleway Service Provider (Entity)
		* Populate Provider fields (Btn)
	+ Option 2: From Server Image Entity List
		* Start from Server Image Entity List
		* List Scaleway Server Images (Btn)
- Restrictions/ Notes:

##### Create Server Image<a name="createScalewayServerImage"></a>
- Required fields:
- Process:
	+ Option 1: from a Server => backup(link)
		* [Backup Server](#backupScalewayServer)
	+ Option 2: from scratch (not yet implemented)
- Restrictions/ Notes:

##### Delete Server Image<a name="deleteScalewayServerImage"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
- Process:
	+ Start from Server Image (Entity)
	+ Delete Scaleway Server Image (Btn)
- Restrictions/ Notes:

#### Server Volume<a name="scalewayServerVolumeScripts"></a>
- Local (l_ssd)
- Block (b_ssd)
- Any volume name can be changed while, for now, only b_ssd volume growing is supported.
- Only one of size, base_volume and base_snapshot may be set.
- Size displayed in bytes
##### List Server Volumes<a name="listScalewayServerVolumes"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
- Process:
	+ Option 1: From Service Provider
		* Start from Scaleway Service Provider (Entity)
		* Populate Provider fields (Btn)
	+ Option 2: From Server Volume Entity List
		* Start from Server Volume Entity List
		* List Scaleway Server Volumes (Btn)
- Restrictions/ Notes:

##### Create Server Volume<a name="createScalewayServerVolume"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Name
	+ Zone
	+ Size (in bytes)
	+ Type
	+ Base Volume (Optional)
	+ Base Snapshot (not implemented)
- Process:
	+ Start from Server Volume Entity List
	+ New (Btn)
	+ Fill in required fields
	+ Save Entity
	+ Create Scaleway Server Volume (Btn)
- Restrictions/ Notes:

##### Update Server Volume<a name="updateScalewayServerVolume"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
	+ Zone
	+ Type (b_ssd)
- Process:
	+ Start from Server Volume (Entity)
	+ Modify fields
	+ Save Entity
	+ Update Scaleway Server Volume (Btn)
- Restrictions/ Notes:

##### Delete Server Volume<a name="deleteScalewayServerVolume"></a>
- Required fields:
	+ Service Provider (Entity) for Scaleway
	+ Provider Side ID
	+ Zone
- Process:
	+ Start from Server Volume (Entity)
	+ Delete Scaleway Server Volume (Btn)
- Restrictions/ Notes:
	+ Check if Volume(s) is not attached to a Server and that Server is not running
