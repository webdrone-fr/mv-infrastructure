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
import org.meveo.credentials.CredentialHelperService;

public class OpenstackAPI extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);
  
  	private String computeBaseAPI = "https://compute.gra11.cloud.ovh.net/v2.1/";
  
  	private String networkBaseAPI = "https://network.compute.gra11.cloud.ovh.net/v2.0/";
  
  	private String imageBaseAPI = "https://image.compute.gra11.cloud.ovh.net/v2/";
  
  	private String identityBaseAPI = "https://auth.cloud.ovh.net/";
  
  	/**
     * Do all of compute api call
     * @param url the path of the call
     * @param token the token currently used by the api
     * @param jsonBody of the request. Can be null if no body is needed
     * @param methodType GET, POST, DELETE, PUT
     * @param objReturn to return is the json response
     * @return the list of json object
     * @throws if the methodType used is not supported
     */
  	public List<JsonObject> computeAPI(String url, String token, String jsonBody, String methodType, String objReturn) throws BusinessException {
      	List<JsonObject> res = new ArrayList<>();
        Client client = ClientBuilder.newClient();
      	if (methodType.equalsIgnoreCase("get")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token).get();
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                for (JsonElement element : rootArray) {
                    JsonObject JObject = element.getAsJsonObject();
                    res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("post")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token).post(Entity.json(jsonBody));
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	// Regarder si la reponse cotient <"servers": [>
              	// Si oui JsonArray
              	// Si non JsonObject direct
              	//if (jp.parse(value).getAsJsonObject().getAsJsonArray(objReturn) instanceof JsonObject) {
                	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                //} else {
                //  	JsonObject obj = new JsonParser().parse(value).getAsJsonObject();
                //  	res.add(obj);
                //}
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("delete")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token).delete();
            String value = response.readEntity(String.class);
            response.close();
        } else if (methodType.equalsIgnoreCase("put")) {
        } else {
          	throw new BusinessException("Cannot found " + methodType + " in method type request. Available methods : get, post, delete, put");
        }
		client.close();
      	return res;
    }
  
	/**
     * Do all of network api call
     * @param url the path of the call
     * @param token the token currently used by the api
     * @param jsonBody of the request. Can be null if no body is needed
     * @param methodType GET, POST, DELETE, PUT
     * @param objReturn to return is the json response
     * @return the list of json object
     * @throws if the methodType used is not supported
     */
  	public List<JsonObject> networkAPI(String url, String token, String jsonBody, String methodType, String objReturn) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.networkBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
  
	/**
     * Do all of image api call
     * @param url the path of the call
     * @param token the token currently used by the api
     * @param jsonBody of the request. Can be null if no body is needed
     * @param methodType GET, POST, DELETE, PUT
     * @param objReturn to return is the json response
     * @return the list of json object
     * @throws if the methodType used is not supported
     */
  	public List<JsonObject> imageAPI(String url, String token, String jsonBody, String methodType, String objReturn) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.imageBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
            for (JsonElement element : rootArray) {
            	JsonObject JObject = element.getAsJsonObject();
              	res.add(JObject);
            }
        }
      	response.close();
      	client.close();
      	return res;
    }
  
	/**
     * Do all of identity api call
     * @param url the path of the call
     * @param token the token currently used by the api
     * @param jsonBody of the request. Can be null if no body is needed
     * @param methodType GET, POST, DELETE, PUT
     * @param objReturn to return is the json response
     * @return the list of json object
     * @throws if the methodType used is not supported
     */
  	public List<JsonObject> IdentityAPI(String url, String token, String jsonBody, String methodType, String objReturn) {
      	List<JsonObject> res = new ArrayList<>();
		Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(this.identityBaseAPI + url);
      	Response response = target.request().header("X-Auth-Token", token).get();
      	String value = response.readEntity(String.class);
      	if (response.getStatus() < 300) {
          	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
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