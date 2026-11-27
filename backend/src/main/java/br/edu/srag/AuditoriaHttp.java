package br.edu.srag;

import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
class AuditoriaHttp implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
                var principal=req.getUserPrincipal();
                var rota=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                LoggerFactory.getLogger("srag.acesso").info("usuario={} metodo={} rota={} status={}",
                    principal==null?"anonimo":principal.getName(),req.getMethod(),rota,res.getStatus());
            }
        }).addPathPatterns("/api/**");
    }
}
