package br.com.fornecedores.controller;

import br.com.fornecedores.dao.UsuarioInterface;
import br.com.fornecedores.dao.UsuarioInterfaceImpl;
import br.com.fornecedores.model.Usuario;
import br.com.fornecedores.util.FacesUtil;
import java.util.Iterator;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;

@ManagedBean
@SessionScoped
public class UsuarioBean {

    public static final String USER_SESSION_KEY = "user";
    private String email;
    private String senha;
    private String tipo;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha.trim();
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String validarUsuario() {
        FacesContext context = FacesContext.getCurrentInstance();

        Usuario user = getUsuario();

        if (user != null) {
            if (!user.getSenha().equals(DigestUtils.sha512Hex(senha))) {
                FacesUtil.setMensagem(FacesMessage.SEVERITY_ERROR,
                        "A senha informada está incorreta.", null, false);
                return null;
            } else if (user.getAtivo().equals("N")) {
                FacesUtil.setMensagem(FacesMessage.SEVERITY_ERROR,
                        "Este usuário não está ativo.", null, false);
                return null;
            }

            context.getExternalContext().getSessionMap().put(USER_SESSION_KEY, user);
            return "index.jsf?faces-redirect=true";

        } else {
            FacesUtil.setMensagem(FacesMessage.SEVERITY_ERROR,
                    "Usuário '" + email + "' não existe ou senha informada está incorreta.", null, false);
            return null;
        }
    }

    public Usuario getUsuario() {
        Usuario user = (Usuario) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("user");
        if (user != null) {
            return user;
        } else {
            UsuarioInterface dao = new UsuarioInterfaceImpl();
            return (Usuario) dao.buscarUsuario(email, DigestUtils.sha512Hex(senha));
        }
    }

    /**
     *
     * @return Destroi sessão e direciona para página de login
     */
    public String logout() {
        HttpSession session = (HttpSession) FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "login.jsf?faces-redirect=true";
    }

    public String getUsuarioLogado() {
        Usuario user = (Usuario) FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("user");
        return user.getEmail();
    }
    
    public void limpaMensagens(){
        Iterator<FacesMessage> msgs = FacesContext.getCurrentInstance().getMessages();
        while (msgs.hasNext()) {
            msgs.next();
            msgs.remove();
        } 
    }
}
