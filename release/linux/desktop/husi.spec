Name: __HUSI_PACKAGE_NAME__
Version: __HUSI_RPM_VERSION__
Release: __HUSI_PKGREL__%{?dist}
Summary: __HUSI_APP_DESCRIPTION__
License: GPL-3.0-or-later
URL: __HUSI_APP_URL__
BuildArch: __HUSI_RPM_ARCH__
Requires: java >= 21

%description
__HUSI_APP_DESCRIPTION__

%prep

%build

%install
mkdir -p %{buildroot}
cp -a %{husi_root}/. %{buildroot}/

%files
/usr/bin/__HUSI_PACKAGE_NAME__
/usr/lib/__HUSI_PACKAGE_NAME__
/usr/share/applications/__HUSI_PACKAGE_NAME__.desktop
__HUSI_PIXMAP_FILE_ENTRY__

%changelog
* __HUSI_CHANGELOG_DATE__ __HUSI_MAINTAINER__ - __HUSI_RPM_VERSION__-__HUSI_PKGREL__
- Package desktop app with system Java runtime dependency
