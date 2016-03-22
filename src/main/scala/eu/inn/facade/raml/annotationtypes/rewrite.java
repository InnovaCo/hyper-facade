package eu.inn.facade.raml.annotationtypes;


import com.mulesoft.raml1.java.parser.core.CustomType;
import eu.inn.facade.raml.RamlAnnotation;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class rewrite extends CustomType implements RamlAnnotation {

    private String uri;

    @XmlElement(name="uri")
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
