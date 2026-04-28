package main

type Library struct {
	UniqueID        string   `json:"uniqueId"`
	ArtifactVersion string   `json:"artifactVersion,omitempty"`
	Name            string   `json:"name,omitempty"`
	Website         string   `json:"website,omitempty"`
	Licenses        []string `json:"licenses"`
	// SCM             SCM      `json:"scm,omitempty"`
}

const (
	LicenseGPL3OrLatter = "GPL-3.0-or-later"
)

/*const (
	SCMPrefix = "scm:"
)

type SCM struct {
	Connection          string `json:"connection,omitempty"`
	DeveloperConnection string `json:"developerConnection,omitempty"`
	URL                 string `json:"url,omitempty"`
}*/
