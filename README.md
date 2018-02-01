
This project illustrates the case of a module which searches in Flickr for images related to specific keywords, downloads the images and writes a record for each downloaded image in a MongoDB collection. 
The core module is a java application which accepts a config file with all required parameters, like the Flickr credentials, the query keywords, a hard disc path for the downloaded images and the MongoDB credentials for writing the records. 


### Prerequisites
1. A MongoDB is installed on the host server, and the user "asgard" is created with access to the database "flickr"

2. A Flickr API key is obtained from the following address: [https://www.flickr.com/services/apps/create/apply](https://www.flickr.com/services/apps/create/apply)

3. The module is compiled and tested. In our case we assume that our module code is written in Java (Netbeans) and it accepts a config file path as a command line parameter. The source code of this module can be inspected in the repository.


### Building the docker image
After downloading the source code and building the executable in Netbeans, the docker image has to be built.
Building the docker image is fairly simple:

```
docker build –t flickr-crawler .
```

The –t parameter specifies the name of the docker image to be created. Please mind the point at the end of the command, which indicates the path to the Dockerfile.

### Running the container
After building the image, we have to create a container as an instantiation of this image. This is done as follows:

```
docker run -p 8000:9876 -v /home/user/flickr/images:/crawler/images flickr-crawler python /crawler/run_service.py 9876
```

Apart from creating a container, this command does the following:
1. It redirects a port of the host to a port of the container (in our case 9876), so that all requests arriving at the host port are redirected to the container port. 
2. It mounts a host folder to a container folder, so that processes from inside the container can access data or resources on the host. In our case, images downloaded from the container will be written to the mounted folder and will be accessible from the host.
3. It starts the web service. In our case it runs the python script which will start listening on the indicated port of the container (in our case 9876).
The container has to be run once. After running the container, it is ready for serving requests.

### Calling the service
Calling the module manually is an action needed to be done by the end-user only for testing purposes. In production, the module will be integrated by the orchestration module, described in deliverable D9.1, and it will be called through that.
Let’s assume that the host’s address is 111.222.333.444, a MongoDB instance is installed on it and the above described container is running.
Calling the module is very simple. Several options exist:

1. A simple REST client plugin for the web browser can be used, as the one below (for Chrome):
[https://chrome.google.com/webstore/detail/advanced-rest-client/hgmloofddffdnphfgcellkdfbfbjeloo](https://chrome.google.com/webstore/detail/advanced-rest-client/hgmloofddffdnphfgcellkdfbfbjeloo)

2. Any client, written in any of the major programming languages, can be used to send programmatically POSTs

3. The CURL command-line tool can be used

The POST should have the following form:

```
	POST http://111.222.333.444:8000/
	Content-Type: application/json
	{
		"flickr":
		{
			"api_key": "XXXXXXXXXXXXXXXXXXXXXXXX",
			"query": "street",
	      		"max_results": 10
		},
		"output_hd": "/crawler/images",
		"output_db":
		{
			"host": "XXX.XXX.XXX.XXX",
			"db": "flickr",
			"collection": "images",
			"user": "asgard",
			"password": "XXXXXXXX"
		}
	}
```

After a while, the downloaded images will be available under the folder "/home/user/flickr/images" of the host and the corresponding records will be stored in the "flickr/images" collection of the MongoDB.

