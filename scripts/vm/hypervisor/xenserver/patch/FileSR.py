#!/usr/bin/env python
# Copyright (c) 2005-2007 XenSource, Inc. All use and distribution of this
# copyrighted material is governed by and subject to terms and conditions
# as licensed by XenSource, Inc. All other rights reserved.
# Xen, XenSource and XenEnterprise are either registered trademarks or
# trademarks of XenSource Inc. in the United States and/or other countries.
#
#
# FileSR: local-file storage repository

import SR, VDI, SRCommand, util, scsiutil, vhdutil
import os, re
import errno
import xml.dom.minidom
import xs_errors
import cleanup
import XenAPI
from lock import Lock

geneology = {}
CAPABILITIES = ["SR_PROBE","SR_UPDATE", \
                "VDI_CREATE","VDI_DELETE","VDI_ATTACH","VDI_DETACH", \
                "VDI_CLONE","VDI_SNAPSHOT","VDI_RESIZE","VDI_RESIZE_ONLINE"]

CONFIGURATION = [ [ 'path', 'path where images are stored (required)' ] ]
                  
DRIVER_INFO = {
    'name': 'Local EXT3 VHD',
    'description': 'SR plugin which represents disks as VHD files stored on a local path',
    'vendor': 'Citrix Systems Inc',
    'copyright': '(C) 2008 Citrix Systems Inc',
    'driver_version': '1.0',
    'required_api_version': '1.0',
    'capabilities': CAPABILITIES,
    'configuration': CONFIGURATION
    }

ENFORCE_VIRT_ALLOC = False
MAX_DISK_MB = 2 * 1024 * 1024
MAX_DISK_METADATA = 4102
VHD_SIZE_INC = 2 * 1024 * 1024
JOURNAL_FILE_PREFIX = ".journal-"

def echo(fn):
    def wrapped(*v, **k):
        name = fn.__name__
        util.SMlog("#### FileSR enter  %s ####" % name )
        res = fn(*v, **k)
        util.SMlog("#### FileSR exit  %s ####" % name )
        return res
    return wrapped



def locking(excType):
    def locking2(op):
        def wrapper(self, *args):
            acquired = False
            if not self.lock.held():
                self.lock.acquire()
                acquired = True
            try:
                try:
                    ret = op(self, *args)
                except (util.SMException, XenAPI.Failure), e:
                    util.logException("FileSR:%s" % op)
                    msg = str(e)
                    if isinstance(e, util.CommandException):
                        msg = "Command %s failed (%s): %s" % \
                                (e.cmd, e.code, e.reason)
                    raise xs_errors.XenError(excType, opterr=msg)
                except:
                    util.logException("FileSR:%s" % op)
                    raise
            finally:
                if acquired:
                    self.lock.release()
            return ret
        return wrapper
    return locking2


class FileSR(SR.SR):
    """Local file storage repository"""

    SR_TYPE = "file"

    @echo
    def handles(srtype):
        return srtype == 'file'
    handles = staticmethod(handles)

    @echo
    def load(self, sr_uuid):
        self.lock = Lock(vhdutil.LOCK_TYPE_SR, self.uuid)
        self.sr_vditype = SR.DEFAULT_TAP
        if not self.dconf.has_key('location') or  not self.dconf['location']:
            raise xs_errors.XenError('ConfigLocationMissing')
        self.path = self.dconf['location']
        self.attached = False

    @echo
    def create(self, sr_uuid, size):
        """ Create the SR.  The path must not already exist, or if it does, 
        it must be empty.  (This accounts for the case where the user has
        mounted a device onto a directory manually and want to use this as the
        root of a file-based SR.) """
        try:
            if util.ioretry(lambda: util.pathexists(self.path)):
                if len(util.ioretry(lambda: util.listdir(self.path))) != 0:
                    raise xs_errors.XenError('SRExists')
            else:
                try:
                    util.ioretry(lambda: os.mkdir(self.path))
                except util.CommandException, inst:
                    if inst.code == errno.EEXIST:
                        raise xs_errors.XenError('SRExists')
                    else:
                        raise xs_errors.XenError('FileSRCreate', \
                              opterr='directory creation failure %d' \
                              % inst.code)
        except:
            raise xs_errors.XenError('FileSRCreate')

    @echo
    def delete(self, sr_uuid):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('SRUnavailable', \
                  opterr='no such directory %s' % self.path)
        cleanup.gc_force(self.session, self.uuid, self.SR_TYPE)

        # check to make sure no VDIs are present; then remove old 
        # files that are non VDI's
        try:
            if util.ioretry(lambda: util.pathexists(self.path)):
                #Load the VDI list
                self._loadvdis()
                for uuid in self.vdis:
                    if not self.vdis[uuid].deleted:
                        raise xs_errors.XenError('SRNotEmpty', \
                              opterr='VDIs still exist in SR')

                # remove everything else, there are no vdi's
                for name in util.ioretry(lambda: util.listdir(self.path)):
                    fullpath =  os.path.join(self.path,name)
                    try:
                        util.ioretry(lambda: os.unlink(fullpath))
                    except util.CommandException, inst:
                        if inst.code != errno.ENOENT and \
                           inst.code != errno.EISDIR:
                            raise xs_errors.XenError('FileSRDelete', \
                                  opterr='failed to remove %s error %d' \
                                  % (fullpath, inst.code))
        except util.CommandException, inst:
            raise xs_errors.XenError('FileSRDelete', \
                  opterr='error %d' % inst.code)

    @echo
    def attach(self, sr_uuid):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('SRUnavailable', \
                  opterr='no such directory %s' % self.path)
        self.attached = True

    @echo
    def detach(self, sr_uuid):
        self.attached = False

    @echo
    @locking("SRScan")
    def scan(self, sr_uuid):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('SRUnavailable', \
                  opterr='no such directory %s' % self.path)

        if not self.vdis:
            self._loadvdis()

        if not self.passthrough:
            self.physical_size = self._getsize()
            self.physical_utilisation  = self._getutilisation()

        for uuid in self.vdis.keys():
            if self.vdis[uuid].deleted:
                del self.vdis[uuid]

        # CA-15607: make sure we are robust to the directory being unmounted beneath
        # us (eg by a confused user). Without this we might forget all our VDI references
        # which would be a shame.
        if not os.path.ismount(self.path):
            util.SMlog("Error: FileSR.scan called but directory %s isn't a mountpoint" % self.path)
            raise xs_errors.XenError('SRUnavailable', \
                                     opterr='not mounted %s' % self.path)

        util.SMlog("Kicking GC")
        cleanup.gc(self.session, self.uuid, self.SR_TYPE, True)

        # default behaviour from here on
        return super(FileSR, self).scan(sr_uuid)

    @echo
    def update(self, sr_uuid):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('SRUnavailable', \
                  opterr='no such directory %s' % self.path)
        self.virtual_allocation = self.session.xenapi.SR.get_virtual_allocation(self.sr_ref)
        self.physical_size = self._getsize()
        self.physical_utilisation  = self._getutilisation()
        self._db_update()
        
    @echo
    def content_type(self, sr_uuid):
        return super(FileSR, self).content_type(sr_uuid)

    @echo
    def vdi(self, uuid, loadLocked = False):
        return FileVDI(self, uuid)

    @echo
    def added_vdi(self, vdi):
        self.vdis[vdi.uuid] = vdi

    @echo
    def deleted_vdi(self, uuid):
        if uuid in self.vdis:
            del self.vdis[uuid]

    @echo
    def _delete_vdi_file(self, path):
        try:
            cmd = ["rm", "-f", path]
            txt = util.pread2(cmd);
        except:
            util.SMlog(" delete vdi file failed ")


    @echo
    def replay(self, uuid):
        try:
            file = open(self.path + "/filelog.txt", "r")
            data = file.readlines()
            file.close()
            self._process_replay(data)
        except:
            raise xs_errors.XenError('SRLog')

    @echo
    def _checkpath(self, path):
        try:
            if util.ioretry(lambda: util.pathexists(path)):
                if util.ioretry(lambda: util.isdir(path)):
                    return True
            return False
        except util.CommandException, inst:
            raise xs_errors.XenError('EIO', \
                  opterr='IO error checking path %s' % path)

    @echo
    def _loadvdis(self):
        if self.vdis:
            return

        pattern = os.path.join(self.path, "*.%s" % SR.DEFAULT_TAP)
        try:
            self.vhds = vhdutil.getAllVHDs(pattern, FileVDI.extractUuid)
        except util.CommandException, inst:
            raise xs_errors.XenError('SRScan', opterr="error VHD-scanning " \
                    "path %s (%s)" % (self.path, inst))
        for uuid in self.vhds.iterkeys():
            if self.vhds[uuid].error:
                raise xs_errors.XenError('SRScan', opterr='uuid=%s' % uuid)
            self.vdis[uuid] = self.vdi(uuid, True)

        # Mark parent VDIs as Read-only and generate virtual allocation
        self.virtual_allocation = 0
        for uuid, vdi in self.vdis.iteritems():
            if vdi.parent:
                if self.vdis.has_key(vdi.parent):
                    self.vdis[vdi.parent].read_only = True
                if geneology.has_key(vdi.parent):
                    geneology[vdi.parent].append(uuid)
                else:
                    geneology[vdi.parent] = [uuid]
            if not vdi.hidden:
                self.virtual_allocation += (vdi.size)

        # now remove all hidden leaf nodes from self.vdis so that they are not 
        # introduced into the Agent DB when SR is synchronized. With the 
        # asynchronous GC, a deleted VDI might stay around until the next 
        # SR.scan, so if we don't ignore hidden leaves we would pick up 
        # freshly-deleted VDIs as newly-added VDIs
        for uuid in self.vdis.keys():
            if not geneology.has_key(uuid) and self.vdis[uuid].hidden:
                util.SMlog("Scan found hidden leaf (%s), ignoring" % uuid)
                del self.vdis[uuid]

    @echo
    def _getsize(self):
        return util.get_fs_size(self.path)
    
    @echo
    def _getutilisation(self):
        return util.get_fs_utilisation(self.path)

    @echo
    def _replay(self, logentry):
        # all replay commands have the same 5,6,7th arguments
        # vdi_command, sr-uuid, vdi-uuid
        back_cmd = logentry[5].replace("vdi_","")
        target = self.vdi(logentry[7])
        cmd = getattr(target, back_cmd)
        args = []
        for item in logentry[6:]:
            item = item.replace("\n","")
            args.append(item)
        ret = cmd(*args)
        if ret:
            print ret

    @echo
    def _compare_args(self, a, b):
        try:
            if a[2] != "log:":
                return 1
            if b[2] != "end:" and b[2] != "error:":
                return 1
            if a[3] != b[3]:
                return 1
            if a[4] != b[4]:
                return 1
            return 0
        except:
            return 1

    @echo
    def _process_replay(self, data):
        logentries=[]
        for logentry in data:
            logentry = logentry.split(" ")
            logentries.append(logentry)
        # we are looking for a log entry that has a log but no end or error
        # wkcfix -- recreate (adjusted) logfile 
        index = 0
        while index < len(logentries)-1:
            if self._compare_args(logentries[index],logentries[index+1]):
                self._replay(logentries[index])
            else:
                # skip the paired one
                index += 1
            # next
            index += 1
 
 
class FileVDI(VDI.VDI):
    @echo
    def load(self, vdi_uuid):
        self.lock = self.sr.lock
        self._loadDecorated(vdi_uuid)

    @echo
    @locking("VDILoad")
    def _loadDecorated(self, vdi_uuid):
        self._load(vdi_uuid)

    @echo
    def _load(self, vdi_uuid):
        self.vdi_type = SR.DEFAULT_TAP
        self.path = os.path.join(self.sr.path, "%s.%s" % \
                                (vdi_uuid,self.vdi_type))

        if self.sr.__dict__.get("vhds") and self.sr.vhds.get(vdi_uuid):
            # VHD info already preloaded: use it instead of querying directly
            vhdInfo = self.sr.vhds[vdi_uuid]
            self.utilisation = vhdInfo.sizePhys
            self.size = vhdInfo.sizeVirt
            self.hidden = vhdInfo.hidden
            if self.hidden:
                self.managed = False
            self.parent = vhdInfo.parentUuid
            if self.parent:
                self.sm_config_override = {'vhd-parent':self.parent}
            else:
                self.sm_config_override = {'vhd-parent':None}
            return

        try:
            # Change to the SR directory in case parent
            # locator field path has changed
            os.chdir(self.sr.path)
        except:
            raise xs_errors.XenError('SRUnavailable')

        if util.ioretry(lambda: util.pathexists(self.path)):
            try:
                st = util.ioretry(lambda: os.stat(self.path))
                self.utilisation = long(st.st_size)
            except util.CommandException, inst:
                if inst.code == errno.EIO:
                    raise xs_errors.XenError('VDILoad', \
                          opterr='Failed load VDI information %s' % self.path)
                else:
                    raise xs_errors.XenError('VDIType', \
                          opterr='Invalid VDI type %s' % self.vdi_type)

            try:
                diskinfo = util.ioretry(lambda: self._query_info(self.path))
                if diskinfo.has_key('parent'):
                    self.parent = diskinfo['parent']
                    self.sm_config_override = {'vhd-parent':self.parent}
                else:
                    self.sm_config_override = {'vhd-parent':None}
                    self.parent = ''
                self.size = long(diskinfo['size']) * 1024 * 1024
                self.hidden = long(diskinfo['hidden'])
                if self.hidden:
                    self.managed = False
                self.exists = True
            except util.CommandException, inst:
                raise xs_errors.XenError('VDILoad', \
                      opterr='Failed load VDI information %s' % self.path)

    @echo
    def update(self, sr_uuid, vdi_location):
        self.load(vdi_location)
        self._db_update()

    @echo
    @locking("VDICreate")
    def create(self, sr_uuid, vdi_uuid, size):
        if util.ioretry(lambda: util.pathexists(self.path)):
            raise xs_errors.XenError('VDIExists')

        # Test the amount of actual disk space
        if ENFORCE_VIRT_ALLOC:
            self.sr._loadvdis()
            reserved = self.sr.virtual_allocation
            sr_size = self.sr._getsize()
            if (sr_size - reserved) < \
               (long(size) + vhdutil.calcOverheadFull(long(size))):
                raise xs_errors.XenError('SRNoSpace')

        try:
            mb = 1024L * 1024L
            size_mb = util.roundup(VHD_SIZE_INC, long(size)) / mb # round up            
            metasize = vhdutil.calcOverheadFull(long(size))
            assert(size_mb > 0)
            assert((size_mb + (metasize/(1024*1024))) < MAX_DISK_MB)
            util.ioretry(lambda: self._create(str(size_mb), self.path))
            self.size = util.ioretry(lambda: self._query_v(self.path))
        except util.CommandException, inst:
            raise xs_errors.XenError('VDICreate', opterr='error %d' % inst.code)
        except AssertionError:
            # Incorrect disk size, must be between 1 MB and MAX_DISK_MB - MAX_DISK_METADATA
            raise xs_errors.XenError('VDISize', opterr='VDI size must be between 1 MB '
                                     + 'and %d MB' % ((MAX_DISK_MB - MAX_DISK_METADATA)-1))
        self.sr.added_vdi(self)

        st = util.ioretry(lambda: os.stat(self.path))
        self.utilisation = long(st.st_size)

        self._db_introduce()
        return super(FileVDI, self).get_params()

    @echo
    @locking("VDIDelete")
    def delete(self, sr_uuid, vdi_uuid):
        if not util.ioretry(lambda: util.pathexists(self.path)):
            return

        if self.attached:
            raise xs_errors.XenError('VDIInUse')

        try:
            util.ioretry(lambda: self._mark_hidden(self.path))
            self.sr._delete_vdi_file(self.path)
        except util.CommandException, inst:
            raise xs_errors.XenError('VDIDelete', opterr='error %d' % inst.code)
        self.sr.deleted_vdi(vdi_uuid)
        self._db_forget()
        
    @echo
    @locking("VDIUnavailable")
    def attach(self, sr_uuid, vdi_uuid):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('VDIUnavailable', \
                  opterr='VDI %s unavailable %s' % (vdi_uuid, self.path))
        try:
            self.attached = True
            if self.sr.srcmd.params.has_key("vdi_ref"):
                vdi_ref = self.sr.srcmd.params['vdi_ref']
                scsiutil.update_XS_SCSIdata(self.session, vdi_ref, vdi_uuid, \
                                        scsiutil.gen_synthetic_page_data(vdi_uuid))
            return super(FileVDI, self).attach(sr_uuid, vdi_uuid)
        except util.CommandException, inst:
            raise xs_errors.XenError('VDILoad', opterr='error %d' % inst.code)

    @echo
    @locking("VDIUnavailable")
    def detach(self, sr_uuid, vdi_uuid):
        self.attached = False

    @echo
    def resize(self, sr_uuid, vdi_uuid, size):
        return self.resize_online(sr_uuid, vdi_uuid, size)

    @echo
    @locking("VDIResize")
    def resize_online(self, sr_uuid, vdi_uuid, size):
        if not self.exists:
            raise xs_errors.XenError('VDIUnavailable', \
                  opterr='VDI %s unavailable %s' % (vdi_uuid, self.path))
        
        if self.hidden:
            raise xs_errors.XenError('VDIUnavailable', opterr='hidden VDI')
        
        if size < self.size:
            util.SMlog('vdi_resize: shrinking not supported: ' + \
                    '(current size: %d, new size: %d)' % (self.size, size))
            raise xs_errors.XenError('VDISize', opterr='shrinking not allowed')
        
        if size == self.size:
            return VDI.VDI.get_params(self)

        size = util.roundup(VHD_SIZE_INC, long(size))
        # Test the amount of actual disk space
        if ENFORCE_VIRT_ALLOC:
            self.sr._loadvdis()
            reserved = self.sr.virtual_allocation
            sr_size = self.sr._getsize()
            delta = long(size - self.size)
            if (sr_size - reserved) < delta:
                raise xs_errors.XenError('SRNoSpace')
        jFile = JOURNAL_FILE_PREFIX + self.uuid
        try:
            vhdutil.setSizeVirt(self.path, size, jFile)
        except:
            # Revert the operation
            vhdutil.revert(self.path, jFile)
            raise xs_errors.XenError('VDISize', opterr='resize operation failed')
        
        self.size = vhdutil.getSizeVirt(self.path)
        st = util.ioretry(lambda: os.stat(self.path))
        self.utilisation = long(st.st_size)
        
        self._db_update()
        return VDI.VDI.get_params(self)

    @echo
    @locking("VDIClone")
    def clone(self, sr_uuid, vdi_uuid):
        dest = util.gen_uuid ()
        args = []
        args.append("vdi_clone")
        args.append(sr_uuid)
        args.append(vdi_uuid)
        args.append(dest)

        if self.hidden:
            raise xs_errors.XenError('VDIClone', opterr='hidden VDI')

        depth = vhdutil.getDepth(self.path)
        if depth == -1:
            raise xs_errors.XenError('VDIUnavailable', \
                  opterr='failed to get VHD depth')
        elif depth >= vhdutil.MAX_CHAIN_SIZE:
            raise xs_errors.XenError('SnapshotChainTooLong')

        # Test the amount of actual disk space
        if ENFORCE_VIRT_ALLOC:
            self.sr._loadvdis()
            reserved = self.sr.virtual_allocation
            sr_size = self.sr._getsize()
            if (sr_size - reserved) < \
               ((self.size + VDI.VDIMetadataSize(SR.DEFAULT_TAP, self.size))*2):
                raise xs_errors.XenError('SRNoSpace')

        newuuid = util.gen_uuid()
        src = self.path
        dst = os.path.join(self.sr.path, "%s.%s" % (dest,self.vdi_type))
        newsrc = os.path.join(self.sr.path, "%s.%s" % (newuuid,self.vdi_type))
        newsrcname = "%s.%s" % (newuuid,self.vdi_type)

        if not self._checkpath(src):
            raise xs_errors.XenError('VDIUnavailable', \
                  opterr='VDI %s unavailable %s' % (vdi_uuid, src))

        # wkcfix: multiphase
        util.start_log_entry(self.sr.path, self.path, args)

        # We assume the filehandle has been released
        try:
            try:
                util.ioretry(lambda: os.rename(src,newsrc))
            except util.CommandException, inst:
                if inst.code != errno.ENOENT:
                    # failed to rename, simply raise error
                    util.end_log_entry(self.sr.path, self.path, ["error"])
                    raise

            try:
                util.ioretry(lambda: self._dualsnap(src, dst, newsrcname))
                # mark the original file (in this case, its newsrc) 
                # as hidden so that it does not show up in subsequent scans
                util.ioretry(lambda: self._mark_hidden(newsrc))
            except util.CommandException, inst:
                if inst.code != errno.EIO:
                    raise

            #Verify parent locator field of both children and delete newsrc if unused
            try:
                srcparent = util.ioretry(lambda: self._query_p_uuid(src))
                dstparent = util.ioretry(lambda: self._query_p_uuid(dst))
                if srcparent != newuuid and dstparent != newuuid:
                    util.ioretry(lambda: os.unlink(newsrc))
            except:
                pass

            # Introduce the new VDI records
            leaf_vdi = VDI.VDI(self.sr, dest) # user-visible leaf VDI
            leaf_vdi.read_only = False
            leaf_vdi.location = dest
            leaf_vdi.size = self.size
            leaf_vdi.utilisation = self.utilisation
            

            base_vdi = VDI.VDI(self.sr, newuuid) # readonly parent
            base_vdi.label = "base copy"
            base_vdi.read_only = True
            base_vdi.location = newuuid
            base_vdi.size = self.size
            base_vdi.utilisation = self.utilisation

            leaf_vdi.sm_config = base_vdi.sm_config = {}
            leaf_vdi.sm_config['vhd-parent'] = newuuid

            try:
                leaf_vdi_ref = leaf_vdi._db_introduce()
                util.SMlog("vdi_clone: introduced VDI: %s (%s)" % (leaf_vdi_ref,dest))
                
                base_vdi_ref = base_vdi._db_introduce()
                vdi_ref = self.sr.srcmd.params['vdi_ref']
                sm_config = self.session.xenapi.VDI.get_sm_config(vdi_ref)
                sm_config['vhd-parent'] = newuuid
                self.session.xenapi.VDI.set_sm_config(vdi_ref, sm_config)
                self.session.xenapi.VDI.set_managed(base_vdi_ref, False)
                util.SMlog("vdi_clone: introduced VDI: %s (%s)" % (base_vdi_ref,newuuid))
            except Exception, e:
                util.SMlog("vdi_clone: caught error during VDI.db_introduce: %s" % (str(e)))
                # Note it's too late to actually clean stuff up here: the base disk has
                # been marked as deleted already.
                util.end_log_entry(self.sr.path, self.path, ["error"])                
                raise
        except util.CommandException, inst:
            # XXX: it might be too late if the base disk has been marked as deleted!
            self._clonecleanup(src,dst,newsrc)
            util.end_log_entry(self.sr.path, self.path, ["error"])
            raise xs_errors.XenError('VDIClone',
                  opterr='VDI clone failed error %d' % inst.code)
        util.end_log_entry(self.sr.path, self.path, ["done"])
        # Return info on the new user-visible leaf VDI
        return leaf_vdi.get_params()
            
    @echo
    def snapshot(self, sr_uuid, vdi_uuid):
        return self.clone(sr_uuid, vdi_uuid)
        
    @echo
    def get_params(self):
        if not self._checkpath(self.path):
            raise xs_errors.XenError('VDIUnavailable', \
                  opterr='VDI %s unavailable %s' % (self.uuid, self.path))
        return super(FileVDI, self).get_params()

    @echo
    def _dualsnap(self, src, dst, newsrc):
        cmd = [SR.TAPDISK_UTIL, "snapshot", SR.DEFAULT_TAP, src, newsrc]
        text = util.pread(cmd)
        cmd = [SR.TAPDISK_UTIL, "snapshot", SR.DEFAULT_TAP, dst, newsrc]
        text = util.pread(cmd)

    @echo
    def _singlesnap(self, src, dst):
        cmd = [SR.TAPDISK_UTIL, "snapshot", SR.DEFAULT_TAP, src, dst]
        text = util.pread(cmd)

    @echo
    def _clonecleanup(self,src,dst,newsrc):
        try:
            util.ioretry(lambda: os.unlink(src))
        except util.CommandException, inst:
            pass
        try:
            util.ioretry(lambda: os.unlink(dst))
        except util.CommandException, inst:
            pass
        try:
            util.ioretry(lambda: os.rename(newsrc,src))
        except util.CommandException, inst:
            pass
      
    @echo
    def _snapcleanup(self,src,dst):
        try:
            util.ioretry(lambda: os.unlink(dst))
        except util.CommandException, inst:
            pass
        try:
            util.ioretry(lambda: os.rename(src,dst))
        except util.CommandException, inst:
            pass

    @echo
    def _checkpath(self, path):
        try:
            if not util.ioretry(lambda: util.pathexists(path)):
                return False
            return True
        except util.CommandException, inst:
            raise xs_errors.XenError('EIO', \
                  opterr='IO error checking path %s' % path)

    @echo
    def _query_v(self, path):
        cmd = [SR.TAPDISK_UTIL, "query", SR.DEFAULT_TAP, "-v", path]
        return long(util.pread(cmd)) * 1024 * 1024

    @echo
    def _query_p_uuid(self, path):
        cmd = [SR.TAPDISK_UTIL, "query", SR.DEFAULT_TAP, "-p", path]
        parent = util.pread(cmd)
        parent = parent[:-1]
        ls = parent.split('/')
        return ls[len(ls) - 1].replace(SR.DEFAULT_TAP,'')[:-1]

    @echo
    def _query_info(self, path):
        diskinfo = {}
        cmd = [SR.TAPDISK_UTIL, "query", SR.DEFAULT_TAP, "-vpf", path]
        txt = util.pread(cmd).split('\n')
        diskinfo['size'] = txt[0]
	for val in filter(util.exactmatch_uuid, [txt[1].split('/')[-1].replace(".%s" % SR.DEFAULT_TAP,"")]):
            diskinfo['parent'] = val
        diskinfo['hidden'] = txt[2].split()[1]
        return diskinfo

    @echo
    def _create(self, size, path):
        cmd = [SR.TAPDISK_UTIL, "create", SR.DEFAULT_TAP, size, path]
        text = util.pread(cmd)

    @echo
    def _mark_hidden(self, path):
        cmd = [SR.TAPDISK_UTIL, "set", SR.DEFAULT_TAP, path, "hidden", "1"]
        text = util.pread(cmd)
        self.hidden = 1

    @echo
    def extractUuid(path):
        fileName = os.path.basename(path)
        uuid = fileName.replace(".%s" % SR.DEFAULT_TAP, "")
        return uuid
    extractUuid = staticmethod(extractUuid)


if __name__ == '__main__':
    SRCommand.run(FileSR, DRIVER_INFO)
else:
    SR.registerSR(FileSR)
