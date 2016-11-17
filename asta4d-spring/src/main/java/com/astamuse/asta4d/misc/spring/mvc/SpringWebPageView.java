/*
 * Copyright 2012 astamuse company,Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.astamuse.asta4d.misc.spring.mvc;

import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.View;

import com.astamuse.asta4d.Context;
import com.astamuse.asta4d.Page;
import com.astamuse.asta4d.template.Template;
import com.astamuse.asta4d.template.TemplateException;
import com.astamuse.asta4d.web.WebApplicationContext;
import com.astamuse.asta4d.web.dispatch.mapping.UrlMappingRule;
import com.astamuse.asta4d.web.dispatch.response.provider.Asta4DPageProvider;

public class SpringWebPageView implements View {

    private static final UrlMappingRule DummyPageRule = new UrlMappingRule().asUnmodifiable();

    private Template template;

    public SpringWebPageView(Template template) throws TemplateException {
        super();
        this.template = template;
    }

    @Override
    public String getContentType() {
        return "";
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        WebApplicationContext context = Context.getCurrentThreadContext();
        for (Entry<String, ?> entry : model.entrySet()) {
            context.setData(entry.getKey(), entry.getValue());
        }
        // since the page will be rendered when the page instance is created, so we have to create the page here rather than at resolver
        new Asta4DPageProvider(new Page(template)).produce(DummyPageRule, response);
    }

}
