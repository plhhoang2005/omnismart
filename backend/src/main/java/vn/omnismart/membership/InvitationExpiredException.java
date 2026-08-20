package vn.omnismart.membership;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class InvitationExpiredException extends RuntimeException {

    public InvitationExpiredException() {
        super("Invitation has expired");
    }
}
