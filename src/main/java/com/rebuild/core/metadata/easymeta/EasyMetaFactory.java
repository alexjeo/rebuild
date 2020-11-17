/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.metadata.easymeta;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.dialect.FieldType;
import cn.devezhao.persist4j.dialect.Type;
import cn.devezhao.persist4j.metadata.BaseMeta;
import cn.devezhao.persist4j.metadata.MetadataException;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.RebuildException;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.impl.FieldExtConfigProps;
import com.rebuild.core.support.state.StateHelper;
import com.rebuild.utils.JSONUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;

import static com.rebuild.core.metadata.easymeta.DisplayType.*;

/**
 * @author devezhao
 * @since 2020/11/17
 */
public class EasyMetaFactory {

    /**
     * @param entity
     * @return
     */
    public static EasyEntity valueOf(Entity entity) {
        return new EasyEntity(entity);
    }

    /**
     * @param entityCode
     * @return
     */
    public static EasyEntity valueOf(int entityCode) {
        return valueOf(MetadataHelper.getEntity(entityCode));
    }

    /**
     * @param entityName
     * @return
     */
    public static EasyEntity valueOf(String entityName) {
        return valueOf(MetadataHelper.getEntity(entityName));
    }

    /**
     * @param field
     * @return
     */
    public static EasyField valueOf(Field field) {
        String displayType = field.getExtraAttrs() == null
                ? null : field.getExtraAttrs().getString("displayType");
        DisplayType dt = displayType == null ? convertBuiltinFieldType(field) : DisplayType.valueOf(displayType);
        if (dt == null) {
            throw new RebuildException("Unsupported field type : " + field);
        }

        try {
            Constructor<?> c = ReflectionUtils.accessibleConstructor(dt.getEasyClass(), Field.class, DisplayType.class);
            return (EasyField) c.newInstance(field, dt);
        } catch (Exception ex) {
            throw new RebuildException(ex);
        }
    }

    /**
     * 将字段类型转成 DisplayType
     *
     * @param field
     * @return
     */
    static DisplayType convertBuiltinFieldType(Field field) {
        Type ft = field.getType();
        if (ft == FieldType.PRIMARY) {
            return ID;
        } else if (ft == FieldType.REFERENCE) {
            int typeCode = field.getReferenceEntity().getEntityCode();
            if (typeCode == EntityHelper.PickList) {
                return PICKLIST;
            } else if (typeCode == EntityHelper.Classification) {
                return CLASSIFICATION;
            } else {
                return REFERENCE;
            }
        } else if (ft == FieldType.ANY_REFERENCE) {
            return ANYREFERENCE;
        } else if (ft == FieldType.REFERENCE_LIST) {
            return N2NREFERENCE;
        } else if (ft == FieldType.TIMESTAMP) {
            return DATETIME;
        } else if (ft == FieldType.DATE) {
            return DATE;
        } else if (ft == FieldType.STRING) {
            return TEXT;
        } else if (ft == FieldType.TEXT || ft == FieldType.NTEXT) {
            return NTEXT;
        } else if (ft == FieldType.BOOL) {
            return BOOL;
        } else if (ft == FieldType.INT || ft == FieldType.SMALL_INT || ft == FieldType.LONG) {
            return NUMBER;
        } else if (ft == FieldType.DOUBLE || ft == FieldType.DECIMAL) {
            return DECIMAL;
        }
        return null;
    }

    /**
     * @param entityOrField
     * @return
     */
    public static BaseEasyMeta<?> valueOf(BaseMeta entityOrField) {
        if (entityOrField instanceof Entity) return valueOf((Entity) entityOrField);
        if (entityOrField instanceof Field) return valueOf((Field) entityOrField);
        throw new MetadataException("Unsupport meta type : " + entityOrField);
    }

    /**
     * @param field
     * @return
     */
    public static DisplayType getDisplayType(Field field) {
        return valueOf(field).getDisplayType();
    }

    /**
     * @param entityName
     * @return
     */
    public static String getLabel(String entityName) {
        return getLabel(MetadataHelper.getEntity(entityName));
    }

    /**
     * @param entityOrField
     * @return
     */
    public static String getLabel(BaseMeta entityOrField) {
        return valueOf(entityOrField).getLabel();
    }

    /**
     * 获取字段 Label（支持两级字段，如 owningUser.fullName）
     *
     * @param entity
     * @param fieldPath
     * @return
     */
    public static String getLabel(Entity entity, String fieldPath) {
        String[] fieldPathSplit = fieldPath.split("\\.");
        Field firstField = entity.getField(fieldPathSplit[0]);
        if (fieldPathSplit.length == 1) {
            return getLabel(firstField);
        }

        Entity refEntity = firstField.getReferenceEntity();
        Field secondField = refEntity.getField(fieldPathSplit[1]);
        return String.format("%s.%s", getLabel(firstField), getLabel(secondField));
    }

    /**
     * 前端使用
     *
     * @param entity
     * @return returns { entity:xxx, entityLabel:xxx, icon:xxx }
     */
    public static JSONObject getEntityShow(Entity entity) {
        EasyEntity easy = valueOf(entity);
        return JSONUtils.toJSONObject(
                new String[]{"entity", "entityLabel", "icon"},
                new String[]{easy.getName(), easy.getLabel(), easy.getIcon()});
    }

    /**
     * 前端使用
     *
     * @param field
     * @return
     */
    public static JSONObject getFieldShow(Field field) {
        JSONObject map = new JSONObject();

        EasyField easy = valueOf(field);
        map.put("name", field.getName());
        map.put("label", easy.getLabel());
        map.put("type", easy.getDisplayType().name());
        map.put("nullable", field.isNullable());
        map.put("creatable", field.isCreatable());
        map.put("updatable", field.isUpdatable());

        DisplayType dt = getDisplayType(field);
        if (dt == REFERENCE || dt == N2NREFERENCE) {
            Entity refEntity = field.getReferenceEntity();
            Field nameField = MetadataHelper.getNameField(refEntity);
            map.put("ref", new String[]{refEntity.getName(), getDisplayType(nameField).name()});
        }
        if (dt == ID) {
            Entity refEntity = field.getOwnEntity();
            Field nameField = MetadataHelper.getNameField(refEntity);
            map.put("ref", new String[]{refEntity.getName(), getDisplayType(nameField).name()});
        } else if (dt == STATE) {
            map.put("stateClass", StateHelper.getSatetClass(field).getName());
        } else if (dt == CLASSIFICATION) {
            map.put("classification", easy.getExtraAttr(FieldExtConfigProps.CLASSIFICATION_USE));
        }

        return map;
    }
}
