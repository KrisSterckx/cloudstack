package net.nuage.vsp.client.rest;


import com.cloud.utils.StringUtils;
import net.nuage.vsp.client.common.model.NuageVspAttribute;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used to process the filter for externalId fields conversion from [objectUuid] to [nuageVspCmsId_objectUuid]
 */
public class FilterProcessor {
    protected static final String[] EXTERNAL_ID_FIELDS = new String[] { NuageVspAttribute.EXTERNAL_ID.getAttributeName() };

    public static String processFilter(String filter, String nuageVspCmsId) {
        if (filter == null) return null;
        if (nuageVspCmsId == null) return filter;

        for (String externalIdField : EXTERNAL_ID_FIELDS) {
            if (!filter.contains(externalIdField)) continue;

            String regex = ".*?" + externalIdField + ".*?['\"](.*?)['\"]( (?i)and ['\"](.*?)['\"])?";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(filter);
            StringBuilder newFilter = new StringBuilder();
            StringBuilder oldFilter = new StringBuilder();
            while (matcher.find()) {
                String output = replaceOldExternalId(matcher.group(0), matcher.group(1), nuageVspCmsId);

                if (matcher.group(3) != null) {
                    output = replaceOldExternalId(output, matcher.group(3), nuageVspCmsId);
                }
                newFilter.append(output);
                oldFilter.append(matcher.group(0));
            }
            filter = newFilter.toString() + filter.substring(oldFilter.length());
        }

        return filter;
    }

    private static String replaceOldExternalId(String input, String oldExternalId, String nuageVspCmsId) {
        if (StringUtils.isNotBlank(oldExternalId) && !oldExternalId.startsWith(nuageVspCmsId)) {
            String newExternalId = nuageVspCmsId + NuageVspConstants.EXTERNAL_ID_DELIMITER + oldExternalId;
            return input.replaceAll(oldExternalId, newExternalId);
        }
        return input;
    }
}
