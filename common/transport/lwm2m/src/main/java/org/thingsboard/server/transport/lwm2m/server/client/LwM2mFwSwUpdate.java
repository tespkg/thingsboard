/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
 */
package org.thingsboard.server.transport.lwm2m.server.client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.leshan.core.request.ContentFormat;
import org.thingsboard.server.common.data.firmware.FirmwareType;
import org.thingsboard.server.common.data.firmware.FirmwareUpdateStatus;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.transport.lwm2m.server.DefaultLwM2MTransportMsgHandler;
import org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.thingsboard.server.common.data.firmware.FirmwareKey.STATE;
import static org.thingsboard.server.common.data.firmware.FirmwareType.FIRMWARE;
import static org.thingsboard.server.common.data.firmware.FirmwareType.SOFTWARE;
import static org.thingsboard.server.common.data.firmware.FirmwareUtil.getAttributeKey;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.EqualsFwSateToFirmwareUpdateStatus;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.EqualsSwSateToFirmwareUpdateStatus;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_NAME_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_PACKAGE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_RESULT_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_STATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_UPDATE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_UPDATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_VER_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LOG_LW2M_INFO;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.EXECUTE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.OBSERVE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.READ;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.WRITE_REPLACE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_INSTALL_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_NAME_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_PACKAGE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_RESULT_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UN_INSTALL_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UPDATE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UPDATE_STATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_VER_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.convertPathFromObjectIdToIdVer;

@Slf4j
public class LwM2mFwSwUpdate {
    // 5/0/6 PkgName
    // 9/0/0 PkgName
    @Getter
    @Setter
    private volatile String currentTitle;
    // 5/0/7 PkgVersion
    // 9/0/1 PkgVersion
    @Getter
    @Setter
    private volatile String currentVersion;
    @Getter
    @Setter
    private volatile UUID currentId;
    @Getter
    @Setter
    private volatile String stateUpdate;
    @Getter
    private String pathPackageId;
    @Getter
    private String pathStateId;
    @Getter
    private String pathResultId;
    @Getter
    private String pathNameId;
    @Getter
    private String pathVerId;
    @Getter
    private String pathInstallId;
    @Getter
    private String pathUnInstallId;
    @Getter
    private String wUpdate;
    @Getter
    @Setter
    private volatile boolean infoFwSwUpdateStart = false;
    @Getter
    @Setter
    private volatile boolean infoFwSwUpdateFinish = false;
    private final FirmwareType type;
    private DefaultLwM2MTransportMsgHandler serviceImpl;
    @Getter
    LwM2mClient lwM2MClient;
    @Getter
    @Setter
    private final List<String> pendingInfoRequestsStart;
    @Getter
    @Setter
    private final List<String> pendingInfoRequestsFinish;

    public LwM2mFwSwUpdate(LwM2mClient lwM2MClient, FirmwareType type) {
        this.lwM2MClient = lwM2MClient;
        this.pendingInfoRequestsStart = new CopyOnWriteArrayList<>();
        this.pendingInfoRequestsFinish = new CopyOnWriteArrayList<>();
        this.type = type;
        this.initPathId();
    }

    private void initPathId() {
        if (this.type.equals(FIRMWARE)) {
            this.pathPackageId = FW_PACKAGE_ID;
            this.pathStateId = FW_STATE_ID;
            this.pathResultId = FW_RESULT_ID;
            this.pathNameId = FW_NAME_ID;
            this.pathVerId = FW_VER_ID;
            this.pathInstallId = FW_UPDATE_ID;
            this.wUpdate = FW_UPDATE;
        } else if (this.type.equals(SOFTWARE)) {
            this.pathPackageId = SW_PACKAGE_ID;
            this.pathStateId = SW_UPDATE_STATE_ID;
            this.pathResultId = SW_RESULT_ID;
            this.pathNameId = SW_NAME_ID;
            this.pathVerId = SW_VER_ID;
            this.pathInstallId = SW_INSTALL_ID;
            this.pathUnInstallId = SW_UN_INSTALL_ID;
            this.wUpdate = SW_UPDATE;
        }
    }

    public void initReadValueStart(DefaultLwM2MTransportMsgHandler serviceImpl, String pathIdVer) {
        if (this.serviceImpl == null) this.serviceImpl = serviceImpl;
        if (pathIdVer != null) {
            this.pendingInfoRequestsStart.remove(pathIdVer);
        }
        if (this.pendingInfoRequestsStart.size() == 0) {
            this.infoFwSwUpdateStart = false;
            boolean conditionalStart = this.type.equals(FIRMWARE) ?  this.conditionalFwUpdateStart() :
                    this.conditionalSwUpdateStart();
            if (conditionalStart) {
                this.updateStart();
            }
        }
    }

    public void initReadValueFinish(DefaultLwM2MTransportMsgHandler serviceImpl, String pathIdVer) {
        if (this.serviceImpl == null) this.serviceImpl = serviceImpl;
        if (pathIdVer != null) {
            this.pendingInfoRequestsFinish.remove(pathIdVer);
        }
        if (this.pendingInfoRequestsFinish.size() == 0) {
            this.infoFwSwUpdateFinish = false;
            boolean conditionalExecute =  this.type.equals(FIRMWARE) ?  this.conditionalFwUpdateExecute() :
                    this.conditionalSwUpdateExecute();
            if (conditionalExecute) {
                this.updateFinish();
                log.warn("ExecuteStart [{}]", this.wUpdate);
            }
        }
    }

    private void updateStart() {
        int chunkSize = 0;
        int chunk = 0;
        byte[] firmwareChunk = this.serviceImpl.firmwareDataCache.get(this.currentId.toString(), chunkSize, chunk);
        String targetIdVer = convertPathFromObjectIdToIdVer(this.pathPackageId, this.lwM2MClient.getRegistration());
        if (this.type.equals(FIRMWARE)) {
            this.observeStateFwUpdate();
        } else {
            this.observeStateSwUpdate();
        }
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(), targetIdVer, WRITE_REPLACE, ContentFormat.OPAQUE.getName(),
                firmwareChunk, this.serviceImpl.config.getTimeout(), null);
        String msg = String.format("%s: Start %s, pkgVer: %s: pkgName - %s.",
                LOG_LW2M_INFO, this.wUpdate, this.currentVersion, this.currentTitle);
        serviceImpl.sendLogsToThingsboard(msg, lwM2MClient.getRegistration().getId());
        log.warn("[{}] [{}] [{}] [{}] [{}] [{}]",
                this.wUpdate,
                this.currentVersion,
                this.lwM2MClient.getResourceValue(null, this.pathVerId),
                this.currentTitle,
                this.lwM2MClient.getResourceValue(null, this.pathNameId),
                this.stateUpdate
        );
    }

    public void updateFinish() {
        if (this.type.equals(FIRMWARE)) {
            this.observeStateFwUpdate();
        } else {
            this.observeStateSwUpdate();
        }
        this.stateUpdate = FirmwareUpdateStatus.DOWNLOADED.name();
        this.sendSateOnThingsboard (null, null, null);
        boolean conditionalExecute =  this.type.equals(FIRMWARE) ?  this.conditionalFwUpdateExecute() :
                this.conditionalSwUpdateExecute();
        if (conditionalExecute) {
            log.warn("ExecuteStart [{}]", this.wUpdate);
            this.serviceImpl.lwM2mTransportRequest.sendAllRequest(this.lwM2MClient.getRegistration(), this.pathInstallId, EXECUTE, ContentFormat.TLV.getName(),
                    null, 0, null);
        }
    }

    /**
     * FW: start
     * Проверяем состояние State (5.3) и Update Result (5.5).
     * 1. Если Update Result > 1 (some errors) - Это означает что пред. апдейт не прошел.
     * - Запускаем апдейт в независимости от состяния прошивки и ее версии.
     * 2. Если Update Result = 1  && State = 0   - Это означает что пред. апдейт прошел.
     * 3. Если Update Result = 0 && State = 0  && Ver = "" - Это означает что апдейта еще не было.
     * - Проверяем поменялась ли версия и запускаем новый апдейт.
     * Новый апдейт:
     * 1. Запись новой прошивки в Lwm2mClient
     * 2. Мониторим итог зиписи:
     * 2.1  State = 2 "Downloaded" и Update Result = 0 "INITIAL" стартуем Update 5.2 (Execute):
     * Мониторим состояние Update Result и State и мапим его на наш enum (DOWNLOADING, DOWNLOADED, VERIFIED, UPDATING, UPDATED, FAILED)
     * + пишем лог (в телеметрию отдельным полем error) с подробным статусом.
     *
     * @valerii.sosliuk Вопрос к клиенту - как будем реагировать на Failed update? Когда повторять операцию?
     * - На update reg?
     * - Или клиент должен послать комканду на рестарт девайса?
     * - или переодически?
     * отправили прошивку мониторим:
     * -- Observe "Update Result" id=5  && "State" id=3
     * --- "Update Result" id=5 value must be = 0
     * ---  "State" id=3  value must be > 0
     * ---  to telemetry - DOWNLOADING
     * "Update Result" id=5 value change > 1  "Firmware updated not successfully" отправили прошивку: telemetry - FAILED
     * "Update Result" id=5 value change  ==1 "State" id=3  value == 0  "Firmware updated  successfully" отправили прошивку: telemetry - UPDATED
     */
    private boolean conditionalFwUpdateStart() {
        Long stateFw = (Long) this.lwM2MClient.getResourceValue(null, this.pathStateId);
        Long updateResultFw = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        String pkgName = (String) this.lwM2MClient.getResourceValue(null, this.pathNameId);
        // #1/#2
        boolean condFwUpdateStart =  updateResultFw > LwM2mTransportUtil.UpdateResultFw.UPDATE_SUCCESSFULLY.code ||
                (
                    (
                        (stateFw == LwM2mTransportUtil.StateFw.IDLE.code && updateResultFw == LwM2mTransportUtil.UpdateResultFw.UPDATE_SUCCESSFULLY.code) ||
                        (stateFw == LwM2mTransportUtil.StateFw.IDLE.code && updateResultFw == LwM2mTransportUtil.UpdateResultFw.INITIAL.code
                                && StringUtils.trimToEmpty(pkgName).isEmpty())
                    ) &&
                    (
                        (this.currentVersion != null && !this.currentVersion.equals(this.lwM2MClient.getResourceValue(null, this.pathVerId))) ||
                        (this.currentTitle != null && !this.currentTitle.equals(this.lwM2MClient.getResourceValue(null, this.pathNameId)))
                    )
                );
        if (condFwUpdateStart) {
            this.sendSateOnThingsboard(stateFw, updateResultFw, pkgName);
        }
        return condFwUpdateStart;
    }

    private boolean conditionalFwUpdateExecute() {
        Long state = (Long) this.lwM2MClient.getResourceValue(null, this.pathStateId);
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        // #1/#2
        return updateResult == LwM2mTransportUtil.UpdateResultFw.INITIAL.code && state == LwM2mTransportUtil.StateFw.DOWNLOADED.code;
    }

    private void observeStateFwUpdate() {
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(this.pathStateId, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, this.serviceImpl.config.getTimeout(), null);
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(this.pathResultId, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, this.serviceImpl.config.getTimeout(), null);
    }

    /**
     * FW: start
     * Проверяем состояние Update_State (9.7) и Update_Result (9.9).
     * 1. Если Update Result > 3 (some errors) - Это означает что пред. апдейт не прошел.
     * - Запускаем апдейт в независимости от состяния прошивки и ее версии.
     * 2. Если Update Result = 2  && Update State = 4   - Это означает что пред. апдейт прошел
     * 3. Если Update Result = 0 && Update State = 0 && Ver = "" - Это означает что апдейта еще не было.
     * 4. Если Update Result = 0 && Update State = 0 - Это означает что пред. апдейт UnInstall
     * - Проверяем поменялась ли версия и запускаем новый апдейт.
     * Новый апдейт:
     * 1. Запись новой прошивки в Lwm2mClient
     * 2. Мониторим итог зиписи:
     * 2.1  Update State = 3 "DELIVERED" стартуем Install 9.4 (Execute):
     * Мониторим состояние Update Result и State и мапим его на наш enum (DOWNLOADING, DOWNLOADED, VERIFIED, UPDATING, UPDATED, FAILED)
     * + пишем лог (в телеметрию отдельным полем error) с подробным статусом.
     */
    private boolean conditionalSwUpdateStart() {
        Long updateState = (Long) this.lwM2MClient.getResourceValue(null, this.pathStateId);
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        String pkgName = (String) this.lwM2MClient.getResourceValue(null, this.pathNameId);
        // #1/#2
        boolean condSwUpdateStart =  updateResult > LwM2mTransportUtil.UpdateResultSw.SUCCESSFULLY_INSTALLED_VERIFIED.code ||
                (
                    (
                        (
                            (
                                (updateState == LwM2mTransportUtil.UpdateStateSw.INSTALLED.code && updateResult == LwM2mTransportUtil.UpdateResultSw.SUCCESSFULLY_INSTALLED.code) ||
                                        (updateState == LwM2mTransportUtil.UpdateStateSw.INITIAL.code && updateResult == LwM2mTransportUtil.UpdateResultSw.INITIAL.code &&
                                                StringUtils.trimToEmpty(pkgName).isEmpty())
                            )
                        ) &&
                        (updateState == LwM2mTransportUtil.UpdateStateSw.INITIAL.code && updateResult == LwM2mTransportUtil.UpdateResultSw.INITIAL.code)
                    ) &&
                    (
                        (this.currentVersion != null && !this.currentVersion.equals(this.lwM2MClient.getResourceValue(null, this.pathVerId))) ||
                        (this.currentTitle != null && !this.currentTitle.equals(this.lwM2MClient.getResourceValue(null, this.pathNameId)))
                    )
                );
        if (condSwUpdateStart) {
            this.sendSateOnThingsboard(updateState, updateResult, pkgName);
        }
        return condSwUpdateStart;

    }

    private boolean conditionalSwUpdateExecute() {
        Long updateState = (Long) this.lwM2MClient.getResourceValue(null, this.pathStateId);
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        // #1/#2
        return (updateResult == LwM2mTransportUtil.UpdateResultSw.INITIAL.code || updateResult == LwM2mTransportUtil.UpdateResultSw.SUCCESSFULLY_INSTALLED_VERIFIED.code) &&
                updateState == LwM2mTransportUtil.UpdateStateSw.DELIVERED.code;
    }

    private void observeStateSwUpdate() {
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(SW_UPDATE_STATE_ID, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, this.serviceImpl.config.getTimeout(), null);
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(SW_RESULT_ID, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, this.serviceImpl.config.getTimeout(), null);
    }

    public void sendSateOnThingsboard (Long state, Long updateResult, String pkgName) {
        if (!FirmwareUpdateStatus.DOWNLOADED.name().equals(this.stateUpdate)) {
            if (StringUtils.trimToEmpty(pkgName).isEmpty()) {
                this.stateUpdate = FirmwareUpdateStatus.DOWNLOADING.name();
            } else if (this.type.equals(FIRMWARE)) {
                this.stateUpdate = EqualsFwSateToFirmwareUpdateStatus(LwM2mTransportUtil.StateFw.fromStateFwByCode(state.intValue()),
                        LwM2mTransportUtil.UpdateResultFw.fromUpdateResultFwByCode(updateResult.intValue())).name();
            } else if (this.type.equals(FirmwareType.SOFTWARE)) {
                this.stateUpdate = EqualsSwSateToFirmwareUpdateStatus(LwM2mTransportUtil.UpdateStateSw.fromUpdateStateSwByCode(state.intValue()),
                        LwM2mTransportUtil.UpdateResultSw.fromUpdateResultSwByCode(updateResult.intValue())).name();
            }
        }
        if (StringUtils.trimToNull(this.stateUpdate) != null) {
            List<TransportProtos.KeyValueProto> result = new ArrayList<>();
            TransportProtos.KeyValueProto.Builder kvProto = TransportProtos.KeyValueProto.newBuilder().setKey(getAttributeKey(this.type, STATE));
            kvProto.setType(TransportProtos.KeyValueType.STRING_V).setStringV(stateUpdate);
            result.add(kvProto.build());
            this.serviceImpl.helper.sendParametersOnThingsboardTelemetry(result,
                    this.serviceImpl.getSessionInfoOrCloseSession(this.lwM2MClient.getRegistration()));
        }
    }

    public void sendReadInfoStart() {
        this.setInfoFwSwUpdateFinish(true);
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathVerId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathNameId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathStateId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathResultId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.forEach(pathIdVer -> {
            this.serviceImpl.lwM2mTransportRequest.sendAllRequest(this.lwM2MClient.getRegistration(), pathIdVer, READ, ContentFormat.TLV.getName(),
                    null, 0, null);
        });
    }
    public void sendReadInfoFinish() {
        this.setInfoFwSwUpdateFinish(true);
        this.pendingInfoRequestsFinish.add(convertPathFromObjectIdToIdVer(
                this.pathVerId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsFinish.add(convertPathFromObjectIdToIdVer(
                this.pathNameId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsFinish.add(convertPathFromObjectIdToIdVer(
                this.pathStateId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsFinish.add(convertPathFromObjectIdToIdVer(
                this.pathResultId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsFinish.forEach(pathIdVer -> {
            this.serviceImpl.lwM2mTransportRequest.sendAllRequest(this.lwM2MClient.getRegistration(), pathIdVer, READ, ContentFormat.TLV.getName(),
                    null, 0, null);
        });
    }
}
