package me.papyapi;

import java.util.logging.Logger;

public interface PapyRequestDispatcher {
    public PapyResponse dispatch(PapyRequest request);


    public void log_info(String msg);
    public void log_warn(String msg);
    public void log_error(String msg);

}
