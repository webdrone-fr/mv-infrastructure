package org.meveo.script.openstack;

import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;
import java.util.List;
import java.util.ArrayList;

public class OpenstackAPI extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);
  
  	private String computeBaseAPI = "https://compute.gra11.cloud.ovh.net/v2.1/";
  
  	private String networkBaseAPI = "https://network.compute.gra11.cloud.ovh.net/v2.0/";
  
  	private String imageBaseAPI = "https://image.compute.gra11.cloud.ovh.net/v2/";
  
  	private String identityBaseAPI = "https://auth.cloud.ovh.net/";
  
  	public List<JsonObject> computeAPI(String url, String token, String jsonBody) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.computeBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	String objectReturned = url.substring(0, url.indexOf("/"));
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objectReturned);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
  
  	public List<JsonObject> networkAPI(String url, String token, String jsonBody) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.networkBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	String objectReturned = url.substring(0, url.indexOf("/"));
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objectReturned);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
  
  	public List<JsonObject> imageAPI(String url, String token, String jsonBody) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.imageBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	String objectReturned = url.substring(0, url.indexOf("/"));
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objectReturned);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
  
  	public List<JsonObject> IdentityAPI(String url, String token, String jsonBody) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.identityBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	String objectReturned = url.substring(0, url.indexOf("/"));
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objectReturned);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
	}
  
	
}