package org.meveo.openstack;

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
import org.meveo.model.customEntities.Credential;
import org.meveo.model.persistence.JacksonUtil;

public class OpenstackAPI extends Script {

    private static final Logger log = LoggerFactory.getLogger(OpenstackAPI.class);

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
    public List<JsonObject> computeAPI(String url, Credential token, String jsonBody, String methodType, String objReturn) throws BusinessException {
        List<JsonObject> res = new ArrayList<>();
        Client client = ClientBuilder.newClient();
        if (methodType.equalsIgnoreCase("get")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).get();
            String value = response.readEntity(String.class);
            log.info("value: {}", JacksonUtil.toStringPrettyPrinted(value));
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("post")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).post(Entity.json(jsonBody));
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("delete")) {
            WebTarget target = client.target(this.computeBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).delete();
            String value = response.readEntity(String.class);
            response.close();
        } else if (methodType.equalsIgnoreCase("put")) {
          	//TODO
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
    public List<JsonObject> networkAPI(String url, Credential token, String jsonBody, String methodType, String objReturn) throws BusinessException {
        List<JsonObject> res = new ArrayList<>();
        Client client = ClientBuilder.newClient();
      	if (methodType.equalsIgnoreCase("get")) {
            WebTarget target = client.target(this.networkBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).get();
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
        	response.close();
        } else if (methodType.equalsIgnoreCase("post")) {
            WebTarget target = client.target(this.networkBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).post(Entity.json(jsonBody));
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
        	response.close();
        } else if (methodType.equalsIgnoreCase("delete")) {
            WebTarget target = client.target(this.networkBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).delete();
            String value = response.readEntity(String.class);
            response.close();
        } else if (methodType.equalsIgnoreCase("put")) {
          	//TODO
        } else {
            throw new BusinessException("Cannot found " + methodType + " in method type request. Available methods : get, post, delete, put");
        }
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
    public List<JsonObject> imageAPI(String url, Credential token, String jsonBody, String methodType, String objReturn) throws BusinessException {
        List<JsonObject> res = new ArrayList<>();
        Client client = ClientBuilder.newClient();
      	if (methodType.equalsIgnoreCase("get")) {
            WebTarget target = client.target(this.imageBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).get();
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("post")) {
            WebTarget target = client.target(this.imageBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).post(Entity.json(jsonBody));
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("delete")) {
            WebTarget target = client.target(this.imageBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).delete();
            String value = response.readEntity(String.class);
            response.close();
        } else if (methodType.equalsIgnoreCase("put")) {
          	//TODO
        } else {
            throw new BusinessException("Cannot found " + methodType + " in method type request. Available methods : get, post, delete, put");
        }
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
    public List<JsonObject> IdentityAPI(String url, Credential token, String jsonBody, String methodType, String objReturn) throws BusinessException {
        List<JsonObject> res = new ArrayList<>();
        Client client = ClientBuilder.newClient();
      	if (methodType.equalsIgnoreCase("get")) {
            WebTarget target = client.target(this.identityBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).get();
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("post")) {
            WebTarget target = client.target(this.identityBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).post(Entity.json(jsonBody));
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
              	String isList = "\"" + objReturn + "s\": [";
                if (value.contains(isList)) {
                  	objReturn += "s";
                    JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray(objReturn);
                    for (JsonElement element : rootArray) {
                        JsonObject JObject = element.getAsJsonObject();
                        res.add(JObject);
                    }
                } else {
                    JsonObject JObject = new JsonParser().parse(value).getAsJsonObject();
                    JObject = JObject.get(objReturn).getAsJsonObject();
                  	res.add(JObject);
                }
            }
            response.close();
        } else if (methodType.equalsIgnoreCase("delete")) {
            WebTarget target = client.target(this.identityBaseAPI + url);
            Response response = target.request().header("X-Auth-Token", token.getToken()).delete();
            String value = response.readEntity(String.class);
            response.close();
        } else if (methodType.equalsIgnoreCase("put")) {
          	//TODO
        } else {
            throw new BusinessException("Cannot found " + methodType + " in method type request. Available methods : get, post, delete, put");
        }
        client.close();
        return res;
    }

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }
}
