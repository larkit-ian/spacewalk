/**
 * Copyright (c) 2013 Red Hat, Inc.
 * All Rights Reserved.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 *
 */
package com.redhat.rhn.frontend.action.iss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.redhat.rhn.common.localization.LocalizationService;
import com.redhat.rhn.common.security.PermissionException;
import com.redhat.rhn.domain.iss.IssFactory;
import com.redhat.rhn.domain.iss.IssSlave;
import com.redhat.rhn.domain.iss.IssSlaveOrgs;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.org.OrgFactory;
import com.redhat.rhn.frontend.dto.OrgDto;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.SessionSetHelper;
import com.redhat.rhn.frontend.struts.StrutsDelegate;
import com.redhat.rhn.frontend.taglibs.list.ListTagHelper;
import com.redhat.rhn.manager.acl.AclManager;
import com.redhat.rhn.manager.rhnset.RhnSetDecl;

/**
 * IssMasterAction extends RhnAction
 *
 * @version $Rev: 1 $
 */
public class IssAllowSlaveOrgsAction extends RhnAction {

    private static final String LIST_NAME = "localOrgsList";

    /** {@inheritDoc} */
    public ActionForward execute(ActionMapping mapping, ActionForm formIn,
            HttpServletRequest request, HttpServletResponse response) {

        if (!AclManager.hasAcl("user_role(satellite_admin)", request, null)) {
            LocalizationService ls = LocalizationService.getInstance();
            PermissionException pex =
                new PermissionException("Only satellite admins can modify allowed-slaves");
            pex.setLocalizedTitle(ls.getMessage("permission.jsp.title.iss.slave"));
            pex.setLocalizedSummary(ls.getMessage("permission.jsp.summary.general"));
            throw pex;
        }

        RequestContext requestContext = new RequestContext(request);
        Long sid = requestContext.getParamAsLong(IssSlave.SID);

        request.setAttribute(ListTagHelper.PARENT_URL,
                        "/rhn/admin/iss/EditSlave.do?sid=" + sid);
        request.setAttribute(IssSlave.SID, sid);


        Set sessionSet = SessionSetHelper.lookupAndBind(request, getSetDecl()
                .getLabel());

        if (!requestContext.isSubmitted()) {
            sessionSet.clear();
        }

        SessionSetHelper helper = new SessionSetHelper(request);

        if (request.getParameter("dispatch") != null) {
            // Before creating entries for whatever selection we have,
            // DELETE THE OLD ONES - less error-prone than trying to do
            // set-theoretic operations based on set-diffs
            IssFactory.clearMapsForSlave(sid);
            helper.updateSet(sessionSet, LIST_NAME);
            return handleDispatchAction(mapping, requestContext, sid, sessionSet);
        }

        List<OrgDto> locals = fromOrgs(OrgFactory.lookupAllOrgs());
        request.setAttribute(LIST_NAME, locals);

        // if its a list action update the set and the selections
        if (ListTagHelper.getListAction(LIST_NAME, request) != null) {
            helper.execute(sessionSet, LIST_NAME, locals);
        }

        // if I have a previous set selections populate data using it
        if (!sessionSet.isEmpty()) {
            helper.syncSelections(sessionSet, locals);
            ListTagHelper.setSelectedAmount(LIST_NAME, sessionSet.size(),
                    request);
        }

        ListTagHelper.bindSetDeclTo(LIST_NAME, getSetDecl(), request);

        Map params = makeParamMap(request);
        if (sid != null) {
            params.put("sid", sid);
        }
        return StrutsDelegate.getInstance().forwardParams(
                mapping.findForward("default"), params);
    }

    private ActionForward handleDispatchAction(ActionMapping mapping,
            RequestContext context,
            Long sid,
            Set sessionSet) {
        Set<String> soids = (Set<String>) sessionSet;
        for (String soid : soids) {
            // Make IssSlaveOrgs entry for this slave/orgid pair
            Long oid = Long.parseLong(soid);
            IssSlaveOrgs iso = new IssSlaveOrgs();
            iso.setOrgId(oid);
            iso.setSlaveId(sid);
            IssFactory.save(iso);
        }
        return mapping.findForward("success");
    }

    protected OrgDto createOrgDto(Long id, String name) {
        OrgDto oi = new OrgDto();
        oi.setId(id);
        oi.setName(name);
        return oi;
    }

    protected List<OrgDto> fromOrgs(List<Org> orgs) {
        List<OrgDto> outList = new ArrayList<OrgDto>();
        for (Org o : orgs) {
            outList.add(createOrgDto(o.getId(), o.getName()));
        }

        Collections.sort(outList, new OrgComparator());

        return outList;
    }

    protected RhnSetDecl getSetDecl() {
        return RhnSetDecl.ISS_LOCAL_ORGS;
    }
}