package com.astamuse.asta4d.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.astamuse.asta4d.Configuration;
import com.astamuse.asta4d.Context;
import com.astamuse.asta4d.extnode.ExtNodeConstants;
import com.astamuse.asta4d.render.transformer.Transformer;
import com.astamuse.asta4d.snippet.SnippetInvokeException;
import com.astamuse.asta4d.snippet.SnippetInvoker;
import com.astamuse.asta4d.snippet.SnippetNotResovlableException;
import com.astamuse.asta4d.template.TemplateException;
import com.astamuse.asta4d.template.TemplateUtil;
import com.astamuse.asta4d.util.ElementUtil;
import com.astamuse.asta4d.util.SelectorUtil;

/**
 * 
 * This class is a functions holder which supply the ability of applying
 * rendereres to certain Element.
 * 
 * @author e-ryu
 * 
 */
public class RenderUtil {

    /**
     * Find out all the snippet in the passed Document and execute them. The
     * Containing embed tag of the passed Document will be exactly mixed in here
     * too. <br>
     * Recursively contained snippets will be executed from outside to inside,
     * thus the inner snippets will not be executed until all of their outer
     * snippets are finished. Also, the dynamically created snippets and embed
     * tags will comply with this rule too.
     * 
     * @param doc
     *            the Document to apply snippets
     * @throws SnippetNotResovlableException
     * @throws SnippetInvokeException
     * @throws TemplateException
     */
    public final static void applySnippets(Document doc) throws SnippetNotResovlableException, SnippetInvokeException, TemplateException {
        if (doc == null) {
            return;
        }
        String selector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS,
                ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_READY);
        List<Element> snippetList = new ArrayList<>(doc.select(selector));
        int snippetListCount = snippetList.size();
        for (int i = snippetListCount - 1; i >= 0; i--) {
            // if parent snippet has not been executed, the current snippet will
            // not be executed too.
            if (isBlockedByParentSnippet(doc, snippetList.get(i))) {
                snippetList.remove(i);
            }
        }

        String renderDeclaration;
        Renderer renderer;
        Context context = Context.getCurrentThreadContext();
        Configuration conf = context.getConfiguration();
        SnippetInvoker invoker = conf.getSnippetInvoker();

        String refId;
        Element renderTarget;
        for (Element element : snippetList) {
            if (!conf.isSkipSnippetExecution()) {
                // for a faked snippet node which is created by template
                // analyzing process, the render target element should be its
                // child.
                if (element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_TYPE).equals(ExtNodeConstants.SNIPPET_NODE_ATTR_TYPE_FAKE)) {
                    renderTarget = element.children().first();
                } else {
                    renderTarget = element;
                }
                context.setCurrentRenderingElement(renderTarget);
                renderDeclaration = element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_RENDER);
                renderer = invoker.invoke(renderDeclaration);
                refId = element.attr(ExtNodeConstants.ATTR_REFID);
                apply(renderTarget, renderer);
                if (element.ownerDocument() == null) {
                    // it means this snippet element is replaced by a element
                    // completely
                    String reSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.ATTR_REFID, refId);
                    Elements elems = doc.select(reSelector);
                    if (elems.size() > 0) {
                        element = elems.get(0);
                    } else {
                        element = null;
                    }
                }
            }

            if (element != null) {
                element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED);
            }
            context.setCurrentRenderingElement(null);
        }

        // load embed nodes which blocking parents has finished
        List<Element> embedNodeList = doc.select(ExtNodeConstants.EMBED_NODE_TAG_SELECTOR);
        int embedNodeListCount = embedNodeList.size();
        Iterator<Element> embedNodeIterator = embedNodeList.iterator();
        Element embed;
        Element embedContent;
        while (embedNodeIterator.hasNext()) {
            embed = embedNodeIterator.next();
            if (isBlockedByParentSnippet(doc, embed)) {
                continue;
            }
            embedContent = TemplateUtil.getEmbedNodeContent(embed);
            TemplateUtil.mergeBlock(doc, embedContent);
            embed.before(embedContent);
            embed.remove();
        }

        if ((snippetListCount + embedNodeListCount) > 0) {
            TemplateUtil.regulateElement(doc);
            applySnippets(doc);
        }
    }

    private final static boolean isBlockedByParentSnippet(Document doc, Element elem) {
        boolean isBlocked;
        String blockingId = elem.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_BLOCK);
        if (blockingId.isEmpty()) {
            // empty block id means there is no parent snippet that need to be
            // aware. if the original block is from a embed template, it means
            // that all of the parent snippets have been finished or this
            // element would not be imported now.
            isBlocked = false;
        } else {
            String parentSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.ATTR_REFID, blockingId);
            Elements parentSnippetSearch = doc.select(parentSelector);
            if (parentSnippetSearch.isEmpty()) {
                isBlocked = false;
            } else {
                Element parentSnippet = parentSnippetSearch.first();
                if (parentSnippet.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS)
                        .equals(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED)) {
                    isBlocked = false;
                } else {
                    isBlocked = true;
                }
            }
        }
        return isBlocked;
    }

    /**
     * Apply given renderer to the given element.
     * 
     * @param target
     *            applying target element
     * @param renderer
     *            a renderer for applying
     */
    public final static void apply(Element target, Renderer renderer) {
        List<Renderer> rendererList = renderer.asUnmodifiableList();
        int count = rendererList.size();
        if (count == 0) {
            return;
        }
        applyClearAction(target, false);
        apply(target, rendererList, 0, count);
    }

    private final static void apply(Element target, List<Renderer> rendererList, int startIndex, int count) {

        if (startIndex >= count) {
            return;
        }

        Renderer currentRenderer = rendererList.get(startIndex);

        if (currentRenderer instanceof GoThroughRenderer) {
            apply(target, rendererList, startIndex + 1, count);
            return;
        }

        String selector = currentRenderer.getSelector();

        if (currentRenderer instanceof DebugRenderer) {
            currentRenderer.getTransformerList().get(0).invoke(target);
            apply(target, rendererList, startIndex + 1, count);
            return;
        }

        List<Element> elemList = new ArrayList<>(target.select(selector));
        List<Transformer<?>> transformerList = currentRenderer.getTransformerList();

        Element delayedElement = null;
        Element resultNode;
        // TODO we suppose that the element is listed as the order from parent
        // to children, so we reverse it. Perhaps we need a real order process
        // to ensure the wanted order.
        Collections.reverse(elemList);
        for (Element elem : elemList) {
            if (elem == target) {
                delayedElement = elem;
                continue;
            }
            for (Transformer<?> transformer : transformerList) {
                resultNode = transformer.invoke(elem);
                elem.before(resultNode);
            }// for transformer
            elem.remove();
        }// for element

        // if the root element is one of the process targets, we can not apply
        // the left renderers to original element because it will be replaced by
        // a new element even it is not necessary.
        if (delayedElement == null) {
            apply(target, rendererList, startIndex + 1, count);
        } else {
            for (Transformer<?> transformer : transformerList) {
                resultNode = transformer.invoke(delayedElement);
                delayedElement.before(resultNode);
                apply(resultNode, rendererList, startIndex + 1, count);
            }// for transformer
            delayedElement.remove();
        }

    }

    /**
     * Clear the redundant elements which are usually created by
     * snippet/renderer applying.If the forFinalClean is true, all the finished
     * snippet tags will be removed too.
     * 
     * @param target
     * @param forFinalClean
     */
    public final static void applyClearAction(Element target, boolean forFinalClean) {
        if (forFinalClean) {
            String removeSnippetSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR,
                    ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED);
            // TODO check if there are unfinished snippet left.
            ElementUtil.removeNodesBySelector(target, removeSnippetSelector, true);
            ElementUtil.removeNodesBySelector(target, ExtNodeConstants.BLOCK_NODE_TAG_SELECTOR, true);

        }

        ElementUtil.removeNodesBySelector(target,
                SelectorUtil.attr(ExtNodeConstants.GROUP_NODE_TAG_SELECTOR, ExtNodeConstants.ATTR_CLEAR_WITH_NS, null), false);
        ElementUtil.removeNodesBySelector(target, SelectorUtil.attr(ExtNodeConstants.ATTR_CLEAR_WITH_NS), false);
        ElementUtil.removeNodesBySelector(target, ExtNodeConstants.GROUP_NODE_TAG_SELECTOR, true);

    }

}
