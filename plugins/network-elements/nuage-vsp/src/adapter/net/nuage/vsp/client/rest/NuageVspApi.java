package net.nuage.vsp.client.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import com.google.common.collect.Maps;
import net.nuage.vsp.client.common.RequestType;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.exception.AuthenticationException;
import net.nuage.vsp.client.exception.NuageVspException;
import net.nuage.vsp.client.exception.UnSupportedNuageEntityException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.cloud.utils.StringUtils;

public class NuageVspApi {
    private static final Logger s_logger = Logger.getLogger(NuageVspApi.class);

    private static long delayFactor = 2;

    public static int s_resourceNotFoundErrorCode = 200;

    public static int s_noChangeInEntityErrorCode = 2039;

    public static int s_duplicateAclPriority = 2591;

    public static int s_networkModificationError = 2506;

    private static String s_internalErrorCode = "internalErrorCode";

    private static String s_internalErrorDetails = "errors";

    private static PoolingClientConnectionManager s_httpClientManager = null;

    private static HttpClient client = null;

    public static void createHttpClient(String protocol, int port) throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, new Integer(120000));
        params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);

        TrustStrategy easyStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                // TODO Auto-generated method stub
                return true;
            }
        };

        SSLSocketFactory sf = new TLSSocketFactory(easyStrategy, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme(protocol, port, sf));
        s_httpClientManager = new PoolingClientConnectionManager(schemeRegistry);
        s_httpClientManager.setDefaultMaxPerRoute(200);
        s_httpClientManager.setMaxTotal(200);

        client = new DefaultHttpClient(s_httpClientManager, params);
    }

    public static volatile String apiKey = "";

    private static ObjectMapper mapper = new ObjectMapper();

    public NuageVspApi() {

    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, String filter, String restRelativePath,
            String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean isCmsUser, String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(requestType, enterpriseName, userName, nuageEntityType.getEntityType(), null, null, null, filter, restRelativePath, cmsUserInfo, noOfRetry,
                retryInterval, true, isCmsUser, nuageVspCmsId);
    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, Object entityDetails,
            String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean isCmsUser, String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(requestType, enterpriseName, userName, nuageEntityType.getEntityType(), null, null, entityDetails, null, restRelativePath, cmsUserInfo,
                noOfRetry, retryInterval, true, isCmsUser, nuageVspCmsId);
    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, String restRelativePath,
            String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean isCmsUser, String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(requestType, enterpriseName, userName, nuageEntityType.getEntityType(), null, null, null, null, restRelativePath, cmsUserInfo, noOfRetry,
                retryInterval, true, isCmsUser, nuageVspCmsId);
    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, String entityId,
            NuageVspEntity nuageChildEntityType, String filter, String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean isCmsUser,
            String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(requestType, enterpriseName, userName, nuageEntityType.getEntityType(), entityId,
                nuageChildEntityType != null ? nuageChildEntityType.getEntityType() : null, null, filter, restRelativePath, cmsUserInfo, noOfRetry, retryInterval,
                true, isCmsUser, nuageVspCmsId);
    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, String entityId,
            NuageVspEntity nuageChildEntityType, Object entityDetails, String filter, String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval,
            boolean checkWarning, boolean isCmsUser, String nuageVspCmsId) throws Exception {
        return executeRestApi(requestType, enterpriseName, userName, nuageEntityType, entityId, nuageChildEntityType, entityDetails, filter, restRelativePath, cmsUserInfo,
                noOfRetry, retryInterval, checkWarning, isCmsUser, null, nuageVspCmsId);
    }

    public static String executeRestApi(RequestType requestType, String enterpriseName, String userName, NuageVspEntity nuageEntityType, String entityId,
            NuageVspEntity nuageChildEntityType, Object entityDetails, String filter, String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval,
            boolean checkWarning, boolean isCmsUser, List<Integer> retryNuageErrorCodes, String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(requestType, enterpriseName, userName, nuageEntityType.getEntityType(), entityId,
                nuageChildEntityType != null ? nuageChildEntityType.getEntityType() : null, entityDetails, filter, restRelativePath, cmsUserInfo, noOfRetry, retryInterval,
                checkWarning, isCmsUser, retryNuageErrorCodes, nuageVspCmsId);
    }

    private static String executeRestApiWithRetry(RequestType type, String enterpriseName, String userName, String entityType, String entityId, String childEntityType,
            Object entityDetails, String filter, String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean checkWarning, boolean isCmsUser,
            String nuageVspCmsId) throws Exception {
        return executeRestApiWithRetry(type, enterpriseName, userName, entityType, entityId, childEntityType, entityDetails, filter, restRelativePath, cmsUserInfo, noOfRetry,
                retryInterval, checkWarning, isCmsUser, null, nuageVspCmsId);
    }

    /**
     * API executes the REST API call and returns the response
     *
     * @param type
     * @param enterpriseName
     * @param userName
     * @param entityType
     * @param entityId
     * @param childEntityType
     * @param entityDetails
     * @param restRelativePath TODO
     * @param cmsUserInfo      TODO
     * @param checkWarning     TODO
     * @return
     * @throws Exception
     */
    private static String executeRestApiWithRetry(RequestType type, String enterpriseName, String userName, String entityType, String entityId, String childEntityType,
            Object entityDetails, String filter, String restRelativePath, String[] cmsUserInfo, int noOfRetry, long retryInterval, boolean checkWarning, boolean isCmsUser,
            List<Integer> retryNuageErrorCodes, String nuageVspCmsId) throws Exception {
        String response = null;
        int attempt = 1;
        long sleepTime = retryInterval;
        Exception exception = null;
        do {
            StringBuffer url = new StringBuffer();
            try {
                response = executeNuageApi(enterpriseName, userName, entityType, entityId, childEntityType, entityDetails, type, filter, restRelativePath, cmsUserInfo,
                        checkWarning, isCmsUser, url, nuageVspCmsId);

                if (attempt > 1) {
                    s_logger.trace(String.format("After %s attempt, exception %s was handled and method %s was successfully executed ", --attempt, exception.getMessage(),
                            "executeHttpRequestWithRetry"));
                }
                return response;
            } catch (Exception e) {
                exception = e;
                if (attempt >= 1) {
                    if (attempt <= noOfRetry) {
                        if (!handleException(attempt, sleepTime, e, "executeHttpRequestWithRetry", restRelativePath, cmsUserInfo, url.toString(), retryNuageErrorCodes)) {
                            throw e;
                        }
                    }

                    attempt++;
                    sleepTime *= delayFactor;
                }
            }
        } while (attempt <= noOfRetry + 1);

        s_logger.error(String.format("Failed to execute %s method even after %s attempts, due to exception %s ", "executeHttpRequestWithRetry", noOfRetry, exception.getMessage()));
        throw exception;
    }

    /**
     * All Nuage APIs will be executed as CMS User in Nuage. This API uses proxy user API to view all the object
     * that are permitted for the user in enterprise. Password of the CMS user will reset when the API fails with
     * Authentication error. This is taken care by retry logic. Look at {@link #login} for
     * more details
     *
     * @param enterpriseName
     * @param userName
     * @param entityType
     * @param entityId
     * @param childEntityType
     * @param entityDetails
     * @param type
     * @param restRelativePath TODO
     * @param cmsUserInfo      TODO
     * @param checkWarning     TODO
     * @return List that contains Map of attributes and their corresponding values
     * @throws Exception
     */
    public static String executeNuageApi(String enterpriseName, String userName, String entityType, String entityId, String childEntityType, Object entityDetails,
                                         RequestType type, String filter, String restRelativePath, String[] cmsUserInfo, boolean checkWarning, boolean isCmsUser,
                                         StringBuffer restUrl, String nuageVspCmsId) throws Exception {
        restUrl = new StringBuffer(restRelativePath);
        String entity = null;
        if (childEntityType != null) {
            restUrl.append("/").append(entityType).append("/").append(entityId).append("/").append(childEntityType);
        } else {
            restUrl.append("/").append(entityType);
        }
        if (type.equals(RequestType.GETALL)) {
            entity = executeHttpMethod(enterpriseName, userName, restUrl, null, type, apiKey, filter, cmsUserInfo, checkWarning, isCmsUser, childEntityType, entityType,
                    restRelativePath, nuageVspCmsId);
        } else if (type.equals(RequestType.GETRELATED)) {
            entity = executeHttpMethod(enterpriseName, userName, restUrl, null, type, apiKey, filter, cmsUserInfo, checkWarning, isCmsUser, childEntityType, entityType,
                    restRelativePath, nuageVspCmsId);
        } else {
            String jsonString = "";
            if (type.equals(RequestType.MODIFY) || type.equals(RequestType.DELETE) || type.equals(RequestType.GET)) {
                restUrl = restUrl.append("/").append(entityId);

            }
            if (entityDetails instanceof List || entityDetails instanceof Map) {
                jsonString = getJsonString(entityDetails);
            } else if (entityDetails instanceof JSONArray) {
                jsonString = ((JSONArray)entityDetails).toString();
            }
            entity = executeHttpMethod(enterpriseName, userName, restUrl, jsonString, type, apiKey, filter, cmsUserInfo, checkWarning, isCmsUser, childEntityType, entityType,
                    restRelativePath, nuageVspCmsId);
        }
        return entity;
    }

    private static String executeHttpMethod(String enterpriseName, String userName, StringBuffer url, String jsonString, RequestType type, String apiKey, String filter,
                                            String[] cmsUserInfo, boolean checkWarning, boolean executeAsCmsUser, String childEntityType, String entityType,
                                            String restRelativePath, String nuageVspCmsId) throws Exception {
        HttpRequestBase httpMethod = null;
        HttpResponse response = null;
        String returnJsonString = null;
        try {
            filter = FilterProcessor.processFilter(filter, nuageVspCmsId);
            jsonString = initContentJson(jsonString, nuageVspCmsId);

            if (!checkWarning) {
                url.append("?responseChoice=1");
            }
            URI uri = new URI(url.toString());
            String proxyUser = "";
            if (!executeAsCmsUser) {
                proxyUser = new StringBuffer(userName).append("@").append(enterpriseName).toString();
            }

            if (type.equals(RequestType.CREATE)) {
                httpMethod = new HttpPost(uri);
                setHttpHeaders(cmsUserInfo, httpMethod, proxyUser, url, executeAsCmsUser, apiKey);
                ((HttpPost)httpMethod).setHeader("Content-Type", "application/json");
                StringEntity entity = new StringEntity(jsonString);
                ((HttpPost)httpMethod).setEntity(entity);
            } else if (type.equals(RequestType.MODIFY) || type.equals(RequestType.MODIFYRELATED)) {
                httpMethod = new HttpPut(uri);
                setHttpHeaders(cmsUserInfo, httpMethod, proxyUser, url, executeAsCmsUser, apiKey);
                ((HttpPut)httpMethod).setHeader("Content-Type", "application/json");
                StringEntity entity = new StringEntity(jsonString);
                ((HttpPut)httpMethod).setEntity(entity);
            } else if (type.equals(RequestType.DELETE)) {
                httpMethod = new HttpDelete(uri);
                setHttpHeaders(cmsUserInfo, httpMethod, proxyUser, url, executeAsCmsUser, apiKey);
            } else
            // GET ALL AND GET case
            {
                httpMethod = new HttpGet(uri);
                setHttpHeaders(cmsUserInfo, httpMethod, proxyUser, url, executeAsCmsUser, apiKey);
                httpMethod.setHeader("accept", "application/json");
                if (StringUtils.isNotBlank(filter)) {
                    httpMethod.setHeader("X-Nuage-Filter", filter);
                }
            }
            long time = System.currentTimeMillis();
            if (client == null) {
                //Re-initialize the HTTP client
                createHttpClient("https", (new URI(restRelativePath).getPort()));
                s_logger.debug("Http client is not initialized. So, it is re-initialized to execute https " + httpMethod + " with url " + url + ".");
            }
            response = client.execute(httpMethod);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Total time taken execute HTTP method " + httpMethod.getMethod() + " " + httpMethod.getURI().getPath() + "  " + (System.currentTimeMillis() - time));
            }
            returnJsonString = parseHttpResponse(response, childEntityType != null ? childEntityType : entityType, type);
        } finally {
            if (httpMethod != null) {
                httpMethod.releaseConnection();
            }
        }
        return initResponseJson(returnJsonString, nuageVspCmsId);
    }

    /**
     * If externalID is used in the request content, parse it to the externalID format used in the VSD (nuageVspCmsId_objectUuid)
     *
     * @param json The request content
     * @return The request content
     */
    private static String initContentJson(String json, String nuageVspCmsId) throws Exception {
        if (StringUtils.isNotBlank(json) && StringUtils.isNotBlank(nuageVspCmsId)) {
            Object entity = mapper.readValue(json, Object.class);
            if (entity instanceof Map) {
                processJsonEntity((Map<String, Object>) entity, nuageVspCmsId, true, false);
                return mapper.writeValueAsString(entity);
            }
        }
        return json;
    }

    /**
     * If externalID is present in the response, parse it to the externalID format used in CS (objectUuid)
     *
     * @param json The response content
     * @return The response content
     */
    private static String initResponseJson(String json, String nuageVspCmsId) throws Exception {
        if (StringUtils.isNotBlank(json) && StringUtils.isNotBlank(nuageVspCmsId)) {
            Object response = mapper.readValue(json, Object.class);
            if (response instanceof Map) {
                processJsonEntity((Map<String, Object>) response, nuageVspCmsId, false, true);
            } else if (response instanceof List) {
                List<Map> entities = (List<Map>) response;
                for (Map<String, Object> entity : entities) {
                    processJsonEntity(entity, nuageVspCmsId, false, true);
                }
            } else {
                return json;
            }
            return mapper.writeValueAsString(response);
        }
        return json;
    }

    private static void processJsonEntity(Map<String, Object> jsonEntity, String nuageVspCmsId, boolean build, boolean revert) {
        String externalIdSuffix = NuageVspConstants.EXTERNAL_ID_DELIMITER + nuageVspCmsId;
        for (String field : jsonEntity.keySet()) {
            if (ArrayUtils.indexOf(FilterProcessor.EXTERNAL_ID_FIELDS, field) != -1) {
                String value = (String) jsonEntity.get(field);
                if (StringUtils.isNotBlank(value)) {
                    boolean endsWithSuffix = value.endsWith(externalIdSuffix);
                    if (revert && endsWithSuffix) {
                        String[] externalIdSplitted = value.split(NuageVspConstants.EXTERNAL_ID_DELIMITER);
                        jsonEntity.put(field, externalIdSplitted[0]);
                    } else if (build && !endsWithSuffix) {
                        String vsdExternalId = value + externalIdSuffix;
                        jsonEntity.put(field, vsdExternalId);
                    }
                }
            } else {
                Object value = jsonEntity.get(field);
                if (value != null && value instanceof Map) {
                    processJsonEntity((Map<String, Object>) value, nuageVspCmsId, build, revert);
                } else if (value != null && value instanceof List) {
                    for (Object entity : (List) value) {
                        if (entity instanceof Map) {
                            processJsonEntity((Map<String, Object>) entity, nuageVspCmsId, build, revert);
                        }
                    }
                }
            }
        }
    }

    private static void setHttpHeaders(String[] cmsUserInfo, HttpRequestBase httpRequest, String proxyUser, StringBuffer url, boolean isCmsUser, String apiKey) {

        //set the authorization header
        String authStr = cmsUserInfo[1] + ":" + apiKey;
        String encoding = Base64.encodeBase64String(authStr.getBytes());
        httpRequest.setHeader("Authorization", "Basic " + encoding);

        httpRequest.setHeader("X-Nuage-Organization", cmsUserInfo[0]);
        if (!isCmsUser && !(url.toString().endsWith(NuageVspEntity.ME.getEntityType()))) {
            httpRequest.setHeader("X-Nuage-ProxyUser", proxyUser);
        }
        httpRequest.setHeader("content-type", "application/json");
    }

    /**
     * Login will set the APIKey value for the CMS User. When any REST API call fails with AuthenticationException
     * a retry will call login and set the correct APIKey. There is not retry for this API.
     *
     * @throws Exception
     */
    public static Map<String, Object> login(String restRelativePath, String[] cmsUserInfo, boolean isCmsUser) throws Exception {
        StringBuffer url = new StringBuffer(restRelativePath);
        url.append("/").append(NuageVspEntity.ME.getEntityType());

        String jsonResponseString = executeHttpMethod(cmsUserInfo[0], cmsUserInfo[1], url, null, RequestType.GET, cmsUserInfo[2], null, cmsUserInfo, true, isCmsUser, null,
                NuageVspEntity.ME.getEntityType(), restRelativePath, null);

        List<Map<String, Object>> jsonResponse = parseJsonString(NuageVspEntity.ME, jsonResponseString);
        if (jsonResponse != null && jsonResponse.size() > 0) {
            Map<String, Object> attributes = jsonResponse.get(0);
            apiKey = ((String)attributes.get("APIKey"));
            return attributes;
        } else {
            apiKey = "";
        }
        return Maps.newHashMap();
    }

    private static String getJsonString(Object entityDetails) throws JsonGenerationException, JsonMappingException, IOException, JSONException {
        String jsonString = "";
        Object json = getJsonFromComplexData(entityDetails);
        if (json != null) {
            jsonString = json.toString();
        }
        return jsonString;
    }

    @SuppressWarnings("unchecked")
    private static Object getJsonFromComplexData(Object entityDetails) throws JsonGenerationException, JsonMappingException, IOException, JSONException {
        if (entityDetails instanceof List) {
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> entity : (List<Map<String, Object>>)entityDetails) {
                jsonArray.put(getJsonFromComplexData(entity));
            }
            return jsonArray;
        }
        if (entityDetails instanceof Map) {
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<String, Object> fields : ((Map<String, Object>)entityDetails).entrySet()) {
                if (fields.getValue() instanceof Map) {
                    jsonObject.put(fields.getKey(), getJsonFromComplexData(fields.getValue()));
                } else if (fields.getValue() instanceof List) {
                    jsonObject.put(fields.getKey(), getJsonFromComplexData(fields.getValue()));
                } else {
                    jsonObject.put(fields.getKey(), fields.getValue() == null ? JSONObject.NULL : fields.getValue());
                }
            }
            return jsonObject;
        }
        return null;
    }

    /**
     * API parses the HTTPRespose for HTTP errors. If the HTTP status is success then it parses the reponse JSON string and returns
     * List that contains Map of attributes and their corresponding values
     *
     * @param httpResponse
     * @param entityType
     * @param type
     * @return List that contains Map of attributes and their corresponding values
     * @throws Exception
     */
    private static String parseHttpResponse(HttpResponse httpResponse, String entityType, RequestType type) throws Exception {
        String httpResponseContent = getHttpResponseContent(httpResponse);
        String errorMessage = "NUAGE HTTP REQUEST FAILED: HTTP Response code: " + httpResponse.getStatusLine().getStatusCode() + " : Response : " + httpResponseContent;
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("HTTP Request result : HTTP status : " + httpResponse.getStatusLine().getStatusCode() + " : Response " + httpResponseContent);
        }

        if (httpResponse.getStatusLine().getStatusCode() >= 402 && httpResponse.getStatusLine().getStatusCode() <= 599) {
            if (!isJson(httpResponseContent)) {
                throw new NuageVspException(httpResponse.getStatusLine().getStatusCode(), errorMessage, entityType, type);
            }

            Map<String, Object> error = parseJsonError(httpResponseContent);
            if (httpResponse.getStatusLine().getStatusCode() == 404) {
                error.put(s_internalErrorCode, s_resourceNotFoundErrorCode);
            }

            if (error.size() > 1) {
                Integer nuageErrorCode = (Integer)error.get(s_internalErrorCode);
                String nuageErrorDetails = (String)error.get(s_internalErrorDetails);
                //TODO: This is a temporary hack to avoid printing internal error code 2039
                if (nuageErrorCode == s_duplicateAclPriority) {
                    s_logger.warn(errorMessage);
                } else if (!(nuageErrorCode == s_noChangeInEntityErrorCode)) {
                    s_logger.error(errorMessage);
                }
                if (type == RequestType.DELETE && nuageErrorCode == s_resourceNotFoundErrorCode) {
                    return httpResponseContent;
                }
                throw new NuageVspException(httpResponse.getStatusLine().getStatusCode(), errorMessage, nuageErrorCode, nuageErrorDetails, entityType, type);
            }
        } else if (httpResponse.getStatusLine().getStatusCode() == 401) {
            s_logger.trace(errorMessage);
            throw new AuthenticationException(errorMessage);
        }
        return httpResponseContent;
    }

    private static boolean isJson(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(input);
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    private static String getHttpResponseContent(HttpResponse httpResponse) throws IOException {
        StringBuilder httpResponseContent = new StringBuilder();
        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            InputStream inputStream = entity.getContent();
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            String json = null;
            while ((json = rd.readLine()) != null) {
                httpResponseContent.append(json);
            }
            inputStream.close();
        }
        return httpResponseContent.toString();
    }

    public static Map<String, Object> parseJsonError(String jsonResult) throws UnSupportedNuageEntityException, JsonParseException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getJsonFactory();
        JsonParser jp = factory.createJsonParser(jsonResult);
        JsonNode actualObj = mapper.readTree(jp);
        Map<String, Object> errorDetails = new HashMap<String, Object>();

        JsonNode internalErrorCode = actualObj.get(s_internalErrorCode);
        JsonNode internalErrorDetails = actualObj.get(s_internalErrorDetails);
        if (internalErrorCode != null) {
            errorDetails.put(s_internalErrorCode, internalErrorCode.getIntValue());
        }
        if (internalErrorDetails != null) {
            errorDetails.put(s_internalErrorDetails, internalErrorDetails.toString());
        }
        return errorDetails;
    }

    public static List<Map<String, Object>> parseJsonString(NuageVspEntity nuageEntity, String jsonResult) throws UnSupportedNuageEntityException, IOException, JsonParseException,
            JsonProcessingException {

        List<Map<String, Object>> entityDetails = new ArrayList<Map<String, Object>>();
        List<NuageVspAttribute> attributes = NuageVspEntity.getAttributes(nuageEntity.getEntityType());
        if (attributes == null) {
            throw new UnSupportedNuageEntityException(nuageEntity.getEntityType() + " is not defined in NuageEntity enum. Please add it if it needs to be supported");
        }
        if (org.apache.commons.lang.StringUtils.isBlank(jsonResult)) {
            return entityDetails;
        }

        StringBuffer jsonBuffer = new StringBuffer();
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getJsonFactory();
        JsonParser jp = factory.createJsonParser(jsonResult);
        JsonNode actualObj = mapper.readTree(jp);
        if (actualObj != null) {
            Iterator<JsonNode> iterator = actualObj.getElements();
            while (iterator.hasNext()) {
                Map<String, Object> attributeValues = new HashMap<String, Object>();
                JsonNode entity = iterator.next();
                jsonBuffer.append(entity.toString());
                Iterator<Map.Entry<String, JsonNode>> entityFields = entity.getFields();
                while (entityFields.hasNext()) {
                    Map.Entry<String, JsonNode> entityField = entityFields.next();
                    for (NuageVspAttribute nuageAttribute : attributes) {
                        if (nuageAttribute.getAttributeName().equals(entityField.getKey())) {
                            Object value = null;
                            if (entityField.getValue().isBoolean()) {
                                value = entityField.getValue().getBooleanValue();
                            } else if (entityField.getValue().isInt()) {
                                value = entityField.getValue().getIntValue();
                            } else if (entityField.getValue().isLong()) {
                                value = entityField.getValue().getLongValue();
                            } else if (entityField.getValue().isTextual()) {
                                value = entityField.getValue().getValueAsText();
                            } else if (!entityField.getValue().isNull()) {
                                value = entityField.getValue().toString();
                            }
                            attributeValues.put(entityField.getKey(), value);
                            break;
                        }
                    }
                }
                entityDetails.add(attributeValues);
            }
        }
        return entityDetails;
    }

    private static boolean handleException(int attempt, long sleepTime, Exception e, String methodName, String restRelativePath, String[] cmsUserInfo, String url,
            List<Integer> retryNuageErrorCodes) throws Exception {
        boolean retry = false;
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        rootCause = rootCause == null ? e : rootCause;

        if (rootCause instanceof AuthenticationException) {
            s_logger.trace(String.format("Authentication failed so failed to execute Nuage VSP API %s", url));
            printRetryMessage(attempt, sleepTime, methodName, url, rootCause);
            try {
                retry = true;
                Thread.sleep(sleepTime);
                NuageVspApi.login(restRelativePath, cmsUserInfo, false);
            } catch (InterruptedException e1) {
                s_logger.warn("Retry sleeping got interrupted");
                throw e;
            }
        } else if (rootCause instanceof SSLException || e instanceof SSLException) {
            printRetryMessage(attempt, sleepTime, methodName, url, rootCause);
            retry = setRetryFlag(sleepTime, e, retry);
        } else if (rootCause instanceof NuageVspException) {
            NuageVspException exception = (NuageVspException)rootCause;
            if (exception.getHttpErrorCode() == 500 || (retryNuageErrorCodes != null && retryNuageErrorCodes.contains(exception.getNuageErrorCode()))) {
                printRetryMessage(attempt, sleepTime, methodName, url, rootCause);
                retry = setRetryFlag(sleepTime, e, retry);
            }
        }
        return retry;
    }

    private static void printRetryMessage(int attempt, long sleepTime, String methodName, String url, Throwable rootCause) {
        s_logger.trace(String.format("Failed to execute Nuage VSP API %s : %s", url, rootCause.getMessage()));
        s_logger.trace(String.format("Attempt %s to re-execute the method %s", attempt, methodName));
        s_logger.trace(String.format("Waiting %s millis before re-executing the method %s", sleepTime, methodName));
    }

    private static boolean setRetryFlag(long sleepTime, Exception e, boolean retry) throws Exception {
        try {
            retry = true;
            Thread.sleep(sleepTime);
        } catch (InterruptedException e1) {
            s_logger.warn("Retry sleeping got interrupted");
            throw e;
        }
        return retry;
    }

    public static class TLSSocketFactory extends SSLSocketFactory {
        public TLSSocketFactory(TrustStrategy trustStategy, X509HostnameVerifier hostNameVerifier) throws KeyManagementException, UnrecoverableKeyException,
                NoSuchAlgorithmException, KeyStoreException {
            super(trustStategy, hostNameVerifier);
        }

        @Override
        protected void prepareSocket(final SSLSocket socket) throws IOException {
            // Strip "SSLv3" from the current enabled protocols.
            String[] protocols = socket.getEnabledProtocols();
            Set<String> set = new HashSet<String>();
            for (String s : protocols) {
                if (s.equals("SSLv3") || s.equals("SSLv2Hello")) {
                    continue;
                }
                set.add(s);
            }
            socket.setEnabledProtocols(set.toArray(new String[0]));
        }
    }
}
