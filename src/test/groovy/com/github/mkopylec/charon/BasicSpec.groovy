package com.github.mkopylec.charon

import com.github.mkopylec.charon.application.TestApplication
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.junit.Rule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.any
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static org.apache.commons.lang3.StringUtils.EMPTY
import static org.apache.commons.lang3.StringUtils.trimToEmpty
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import static org.springframework.http.HttpStatus.OK
import static org.springframework.http.ResponseEntity.status

@DirtiesContext
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication)
abstract class BasicSpec extends Specification {

    @Rule
    public WireMockRule localhost8080 = new WireMockRule(8080)
    @Rule
    public WireMockRule localhost8081 = new WireMockRule(8081)

    @Shared
    private RestTemplate restTemplate = new RestTemplate()
    @LocalServerPort
    protected int port
    @Autowired
    private ServerProperties server

    void setup() {
        fixWiremock()
        stubResponse(OK)
    }

    protected ResponseEntity<String> sendRequest(HttpMethod method, String uri, Map<String, String> headers = [:], String body = EMPTY) {
        def url = "http://localhost:$port$contextPath$uri".toString()
        def httpHeaders = new HttpHeaders()
        headers.each { name, value -> httpHeaders.put(name, value.split(', ') as List<String>) }
        def request = new HttpEntity<>(body, httpHeaders)
        try {
            def exchange = restTemplate.exchange(url, method, request, String)
            return exchange
        } catch (HttpStatusCodeException e) {
            return status(e.getStatusCode())
                    .headers(e.responseHeaders)
                    .body(e.responseBodyAsString)
        }
    }

    protected String getContextPath() {
        return trimToEmpty(server.servlet.contextPath)
    }

    protected void stubDestinationResponse(boolean timedOut) {
        stubResponse(OK, [:], null, timedOut)
    }

    protected void stubDestinationResponse(HttpStatus responseStatus) {
        stubResponse(responseStatus)
    }

    protected void stubDestinationResponse(Map<String, String> responseHeaders) {
        stubResponse(OK, responseHeaders)
    }

    protected void stubDestinationResponse(HttpStatus responseStatus, Map<String, String> responseHeaders) {
        stubResponse(responseStatus, responseHeaders)
    }

    protected void stubDestinationResponse(String responseBody) {
        stubResponse(OK, [:], responseBody)
    }

    protected void stubDestinationResponse(HttpStatus responseStatus, String responseBody) {
        stubResponse(responseStatus, [:], responseBody)
    }

    private void stubResponse(HttpStatus responseStatus = OK, Map<String, String> responseHeaders = [:], String responseBody = null, boolean timedOut = false) {
        [localhost8080, localhost8081].each {
            def response = aResponse()
            responseHeaders.each { name, value -> response = response.withHeader(name, value) }
            if (responseBody) {
                response = response.withBody(responseBody)
            }
            if (timedOut) {
                response = response.withFixedDelay(1000)
            }
            response = response.withStatus(responseStatus.value())
            it.stubFor(any(urlMatching('.*')).willReturn(response))
        }
    }

    // TODO Wiremock fix https://github.com/tomakehurst/wiremock/issues/97
    private static void fixWiremock() {
        System.setProperty('http.keepAlive', 'false')
        System.setProperty('http.maxConnections', '1')
    }
}
