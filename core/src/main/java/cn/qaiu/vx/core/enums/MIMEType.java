package cn.qaiu.vx.core.enums;

/**
 * MIMEType: request or response head
 * <br>Create date 2021/8/30 4:35
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public enum MIMEType {

    NULL(""),
    ALL("*/*"),
    TEXT_HTML("text/html"),
    APPLICATION_POSTSCRIPT("application/postscript"),
    TEXT_PLAIN("text/plain"),
    APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    MULTIPART_FORM_DATA("multipart/form-data"),
    APPLICATION_X_JAVA_AGENT("application/x-java-agent"),
    MESSAGE_HTTP("message/http"),
    TEXT_CSS("text/css"),
    TEXT_XML("text/xml"),
    TEXT("text/*"),
    APPLICATION_RDF_XML("application/rdf+xml"),
    APPLICATION_XHTML_XML("application/xhtml+xml"),
    APPLICATION_XML("application/xml"),
    APPLICATION_JSON("application/json");

    public String getValue() {
        return type;
    }

    private final String type;

    MIMEType(String type) {
        this.type = type;
    }

}
