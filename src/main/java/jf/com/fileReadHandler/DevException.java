package jf.com.fileReadHandler;

import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class DevException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private String msg;

    public DevException(String msg) {
        super(msg);
        this.msg = msg;
    }

}
