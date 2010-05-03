#!/usr/bin/env python
# Copyright (c) 2005-2007 XenSource, Inc. All use and distribution of this
# copyrighted material is governed by and subject to terms and conditions
# as licensed by XenSource, Inc. All other rights reserved.
# Xen, XenSource and XenEnterprise are either registered trademarks or
# trademarks of XenSource Inc. in the United States and/or other countries.
#
#
# LUNperVDI: Generic Raw LUN handler, used by HBASR and ISCSISR
#

import SR, VDI, SRCommand, util
import os, sys
import scsiutil
import xs_errors

def echo(fn):
    def wrapped(*v, **k):
        name = fn.__name__
        util.SMlog("$$$$ LUNperVDI enter  %s $$$$" % name )
        res = fn(*v, **k)
        util.SMlog("$$$$ LUNperVDI exit  %s $$$$" % name )
	return res
    return wrapped


class RAWVDI(VDI.VDI):
    @echo
    def load(self, vdi_uuid):
        if not self.sr.attached:
            raise xs_errors.XenError('SRUnavailable')

        self.uuid = vdi_uuid
        self.location = vdi_uuid
        self.managed = True
        try:
            vdi_ref = self.sr.session.xenapi.VDI.get_by_uuid(vdi_uuid)
            self.managed = self.sr.session.xenapi.VDI.get_managed(vdi_ref)
            self.sm_config = self.sr.session.xenapi.VDI.get_sm_config(vdi_ref)
            self.path = self.sr.mpathmodule.path(self.sm_config['SCSIid'])
            util.SMlog("$$$$ managed   %s, sm_config %s" %(self.managed, self.sm_config ))
        except:
            pass

    @echo
    def _query(self, path, id):
        self.uuid = scsiutil.gen_uuid_from_string(scsiutil.getuniqueserial(path))
        self.location = self.uuid
        self.vendor = scsiutil.getmanufacturer(path)
        self.serial = scsiutil.getserial(path)
        self.LUNid = id
        self.size = scsiutil.getsize(path)
        self.SCSIid = scsiutil.getSCSIid(path)
        self.path = path
        self.managed = True
        sm_config = util.default(self, "sm_config", lambda: {})
        sm_config['LUNid'] = str(self.LUNid)
        sm_config['SCSIid'] = self.SCSIid
        self.sm_config = sm_config

    @echo
    def getuniqueserial(self):
        try:
	    scsiid = self.sm_config['SCSIid']
            cmd = ["echo", "%s" % scsiid]
            text = util.pread2(cmd)
            cmd = ["md5sum"]
            txt = util.pread3(cmd, text)
            return txt.split(' ')[0]
        except:
            return ''


    @echo
    def introduce(self, sr_uuid, vdi_uuid):
        self.sm_config = self.sr.srcmd.params['vdi_sm_config']
        util.SMlog("introduce %s , %s" % (vdi_uuid , self.sm_config))
	self.uuid = scsiutil.gen_uuid_from_string(self.getuniqueserial())
	self.location = self.location
        vdi_uuid = self.uuid
        self.sr.vdis[vdi_uuid] = self

        try:
            util._getVDI(self.sr, vdi_uuid)
            self.sr.vdis[vdi_uuid]._db_update()
        except:
            self.sr.vdis[vdi_uuid]._db_introduce()
        return super(RAWVDI, self).get_params()

    @echo
    def create(self, sr_uuid, vdi_uuid, size):
        VDIs = util._getVDIs(self.sr)
        self.sr._loadvdis()
        smallest = 0
        for vdi in VDIs:
            if not vdi['managed'] \
                   and long(vdi['virtual_size']) >= long(size) \
                   and self.sr.vdis.has_key(vdi['uuid']):
                if not smallest:
                    smallest = long(vdi['virtual_size'])
                    v = vdi
                elif long(vdi['virtual_size']) < smallest:
                    smallest = long(vdi['virtual_size'])
                    v = vdi
        if smallest > 0:
            self.managed = True
            self.sr.session.xenapi.VDI.set_managed(v['vdi_ref'], self.managed)
            return super(RAWVDI, self.sr.vdis[v['uuid']]).get_params()
        raise xs_errors.XenError('SRNoSpace')

    @echo
    def delete(self, sr_uuid, vdi_uuid):
        try:
            vdi = util._getVDI(self.sr, vdi_uuid)
            #if not vdi['managed']:
            return
            sm_config = vdi['sm_config']
            self.sr.session.xenapi.VDI.set_managed(vdi['vdi_ref'], False)
        except:
            pass
        

    @echo
    def attach(self, sr_uuid, vdi_uuid):
	"""
        self.sr._loadvdis()
        if not self.sr.vdis.has_key(vdi_uuid):
            raise xs_errors.XenError('VDIUnavailable')
        if not util.pathexists(self.path):
            self.sr.refresh()
            if not util.wait_for_path(self.path, MAX_TIMEOUT):
                util.SMlog("Unable to detect LUN attached to host [%s]" % self.sr.path)
                raise xs_errors.XenError('VDIUnavailable')
	"""
        return super(RAWVDI, self).attach(sr_uuid, vdi_uuid)

    @echo
    def detach(self, sr_uuid, vdi_uuid):
	return
        self.sr._loadvdis()
        if not self.sr.vdis.has_key(vdi_uuid):
            raise xs_errors.XenError('VDIUnavailable')

    @echo
    def _set_managed(self, vdi_uuid, managed):
        try:
            vdi = util._getVDI(self.sr, vdi_uuid)
            self.sr.session.xenapi.VDI.set_managed(vdi['vdi_ref'], managed)
        except:
            raise xs_errors.XenError('VDIUnavailable')

