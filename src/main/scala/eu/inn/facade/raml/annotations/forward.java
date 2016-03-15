package eu.inn.facade.raml.annotations;


import com.mulesoft.raml1.java.parser.core.CustomType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class forward extends CustomType implements RamlAnnotation {

    private String uri;

    @XmlElement(name="uri")
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
