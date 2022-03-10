# Read Me
## Table of Contents
1. [requirement](#requirements)
2. [Quickstart](#quickstart)
3. [Entities](#entities)
## Requirements<a name="requirements"></a>
### [mv-credentials](#https://github.com/webdrone-fr/mv-credentials)
#### Cloudflare
- Token
#### OVH/ OpenAPI
- Token
- SSH key
- Token needs to refreshed (on a weekly basis?)
	+ checked at each call with an additional day added to expiry date if ok
#### Scaleway
- Token
#### [mv-infrastructure](#https://github.com/webdrone-fr/mv-infrastructure)
##### Cloudflare
- Service Provider
	+ API Url
##### OVH/ OpenAPI
- Service Provider
	+ API Url
##### Scaleway
- Service Provider
	+ API Url
	+ Populate Provider fields (action btn)
## Quickstart<a name="quickstart"></a>
1. Launch meveo container
2. Install mv-credential + mv-infrastructure from github
2. Credential
	1. Check / ensure credential for desired provider is present
		1. If OVH, need valid ssh token
	2. If not present, create credential with fields:
		- API Url
		- API token
		- Username
		- Authentication method
			+ (List methods by provider)
3. Service Provider
	1. Check/ ensure Entity for required service provider is present
	2. If not, create Entity with fields:
		- API Url
		- Code
		- Description
	3. Populate provider fields (Btn)
	4. Look at available server types, images (if required) and available Public IPs(Scaleway)
4. Server
	1. List servers (limited to names starting with "dev-")
	2. To create a server:
		1. From an image (recomended):
			1. Select an image(get id)
			2. Create a new (Provider)Server Entity
				1. Fill fields:
					1. name
					2. zone
					3. type
					4. image
					5. provider
				2. Save Entity
				3. Btn Create Server
		2. From scratch: (need to verify procedure)
			1. Create a local volume of appropriate size
			2. Create a new (Provider)Server Entity
				1. Fill fields:
					1. name
					2. zone
					3. type
					4. root volume with id of previously created volume
					5. provider
				2. Save Entity
				3. Btn Create Server
5. Dns Record
	1. Create Entity Dns Record
	2. Fill in fields:
		1. Domain
		2. Name
		3. Type
		4. Value (Address)
	3. Save Entity
	4. Btn Create dns record
6. Lockdown rule
	1. List lockdown rules (from domain)
	2. Create or update lockdown rule :(limited availability of concurrent active rules)
	3. Add dns record to lockdown rule
		1. Includes address and domain for server
### Entities (For terminology and restrictions)<a name="entities"></a>
#### Server
Short term v long term availability requirements for server: long term better to use OVH as more economical
- Contains common fields accross servers from different providers
##### OVH Server
- keyname
- server network
- Authentication used :
- API Url: SERVICE.ZONE.cloud.ovh.net/v2.1/
##### Scaleway Server
- Local volume size requirements must be met, depend on server type
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

#### Server Volume
A volume is where you store your data inside your instance. It appears as a block device on Linux that you can use to create a filesystem and mount it.

size displayed in bytes
l_ssd v b_ssd
#### Server Image
Images are backups of your instances. You can reuse that image to restore your data or create a series of instances with a predefined configuration.

An image is a complete backup of your server including all volumes.
- Public v private
#### Security Group
A security group is a set of firewall rules on a set of instances. Security groups enable to create rules that either drop or allow incoming traffic from certain ports of your instances.
- Defines group for which security rules apply
#### Public IP
- Independent IP that can be allocated to/ removed from a server/ passed from one server to another
#### Bootscript
Bootscripts are a combination of a Kernel and of an initrd. They tell to the instance how to start and configure its starting process and settings.
#### Security Rule
- Rule to determine connection to/ from a server and intended behaviour
#### Server Action
- Action performed on a server => poweron, poweroff, backup, stopinplace, terminate and reboot
/tasks for check action status => not listed in documentation
#### Server Network (OVH)
#### Server User Data
User data is a key value store API you can use to provide data from and to your server without authentication.
- Data that can be passed to a server prior to launch/ creation

#### Dns Record
- attached to a domain name
#### Domain Name
#### Lockdown Rule
- Associated to a domain name and concerns the different dns records of that domain name
## Scaleway
Scaleway Documentation url = https://developers.scaleway.com/en/products/instance/api/
version = v1
### Create Server
#### Required fields
- Name
- Provider 
- Zone
- Type
##### From Image
- Get image id
- local volumes will be automatically created to meet size requirements for server type, additional block volumes can be added for further storage
##### Without Image
- Create volume
	+ Ensure meets size requirements for server type
- Bootscript
	+ Is part of image, can be allocated automatically or select from list
	+ requires boottype to be set to bottscript(default is local)
### Update Server
- cannot change the image of a server
- Cannot add/ remove a local volume to a running server
- adding/ removing local volumes must meet size requirements for server type
### Delete Server
- Ensure Server is not running
- Terminate ( not yet implemented)
- delete without removing volumes
- delete as well as volumes

### Backup Server
- Creates an image + associated volumes
- limited to the same zone as the original server (TBC)
### Security groups
#### List security groups
#### Create Security Group
- limit to max number of active security groups, based on account type(TBC)
#### Add server to security group/ update security group
#### List security group rules
### Public Ip
#### Reserve Public IP (not yet implemented and to be tested)
#### List public IPs
- will not refer to a server if server does not exist as an entity on meveo ie does not start with "dev-", see list of public ips in the service provider entity for full details
### Server Volumes
#### Create Server Volume
- Required fields:
	+ name
	+ size
	+ type
	+ zone
#### Update Volume
- local volume can have name updated only
- blockvolume can have size and name updated
#### Delete Volume
- Check if volume is not attached to a server and that server is not running

### Server Images
#### List Server Images
#### Create Server Image
- from scratch (not yet implemented)
- from a server => backup

## OVH/ OpenAPI
OpenAPI documentation url = 
Version = 


## Cloudflare
### List Domains
### List DNS records for domain
### List Lockdown rules for domain
### Create DNS record + add to lockdown rule
