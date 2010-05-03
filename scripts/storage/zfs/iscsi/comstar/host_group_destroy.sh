#!/usr/bin/env bash
# host_group_destroy.sh -- delete all iSCSI host groups
#
# Usage:  host_group_destroy.sh
#
# Removes all iSCSI host groups that are not in use.
#
# OpenSolaris

# Delete iSCSI host groups
host_groups=$(stmfadm list-hg | cut -d' ' -f 3)

for host_group in $host_groups
do
        stmfadm delete-hg $host_group
done

