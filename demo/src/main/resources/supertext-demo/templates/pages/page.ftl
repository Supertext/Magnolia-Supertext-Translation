[#-- Supertext demo page: title, introduction and the "main" area. --]
<!DOCTYPE html>
<html lang="${cmsfn.language()}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex">
  <title>${content.title!"Supertext demo"}</title>
  [@cms.page /]
  <style>
    body { font-family: system-ui, sans-serif; max-width: 46rem; margin: 2rem auto; padding: 0 1rem; color: #1d2733; line-height: 1.55; }
    h1 { font-size: 2rem; margin-bottom: .25rem; }
    .intro { font-size: 1.2rem; color: #4a5866; margin-top: 0; }
    blockquote { border-left: 4px solid #e30613; margin: 1.5rem 0; padding: .25rem 1rem; font-size: 1.15rem; }
    blockquote footer { font-size: .9rem; color: #4a5866; }
  </style>
</head>
<body>
  <h1>${content.title!}</h1>
  [#if content.intro?has_content]<p class="intro">${content.intro}</p>[/#if]
  [@cms.area name="main" /]
</body>
</html>
