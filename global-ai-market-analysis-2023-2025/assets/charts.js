// assets/charts.js — Global AI Market Analysis Report Charts
(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  var palette = [accent, accent2, muted, accent + '99', accent2 + '99', accent + '66', accent2 + '66'];

  // Common tooltip style
  var tooltipStyle = {
    trigger: 'axis',
    appendToBody: true,
    backgroundColor: '#111827',
    borderColor: '#1e293b',
    textStyle: { color: '#e8ecf4', fontSize: 13 }
  };

  // Common axis label style
  var axisLabelStyle = { color: muted, fontSize: 12 };
  var axisLineStyle = { lineStyle: { color: rule } };
  var splitLineStyle = { lineStyle: { color: rule, type: 'dashed' } };

  // --- Chart 1: Global AI IT Investment Trend (2023-2028) ---
  var chart1 = echarts.init(document.getElementById('chart-market-size'), null, { renderer: 'svg' });
  chart1.setOption({
    animation: false,
    tooltip: tooltipStyle,
    grid: { left: 60, right: 30, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: ['2023', '2024', '2025F', '2026F', '2027F', '2028F'],
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      name: 'USD Billions',
      nameTextStyle: { color: muted, fontSize: 11 },
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: splitLineStyle
    },
    series: [{
      type: 'bar',
      data: [
        { value: 154, itemStyle: { color: accent } },
        { value: 315.8, itemStyle: { color: accent } },
        { value: 419.5, itemStyle: { color: accent + 'cc' } },
        { value: 558.3, itemStyle: { color: accent + 'cc' } },
        { value: 672.1, itemStyle: { color: accent + 'cc' } },
        { value: 815.9, itemStyle: { color: accent2 } }
      ],
      barWidth: '50%',
      label: {
        show: true,
        position: 'top',
        color: ink,
        fontSize: 11,
        formatter: function(p) { return '$' + p.value + 'B'; }
      }
    }]
  });
  window.addEventListener('resize', function() { chart1.resize(); });

  // --- Chart 2: Global Private AI Investment & Gen AI Funding (2021-2024) ---
  var chart2 = echarts.init(document.getElementById('chart-investment'), null, { renderer: 'svg' });
  chart2.setOption({
    animation: false,
    tooltip: tooltipStyle,
    legend: {
      data: ['Total Private AI Investment', 'Generative AI Investment'],
      textStyle: { color: muted, fontSize: 12 },
      top: 5
    },
    grid: { left: 60, right: 30, top: 45, bottom: 50 },
    xAxis: {
      type: 'category',
      data: ['2021', '2022', '2023', '2024'],
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      name: 'USD Billions',
      nameTextStyle: { color: muted, fontSize: 11 },
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: splitLineStyle
    },
    series: [
      {
        name: 'Total Private AI Investment',
        type: 'bar',
        data: [96.1, 61.4, 55.9, 100.4],
        itemStyle: { color: accent },
        barWidth: '30%',
        label: { show: true, position: 'top', color: ink, fontSize: 11, formatter: '${c}B' }
      },
      {
        name: 'Generative AI Investment',
        type: 'bar',
        data: [0, 3.2, 25.2, 33.9],
        itemStyle: { color: accent2 },
        barWidth: '30%',
        label: { show: true, position: 'top', color: ink, fontSize: 11, formatter: '${c}B' }
      }
    ]
  });
  window.addEventListener('resize', function() { chart2.resize(); });

  // --- Chart 3: Regional Private AI Investment (2024) ---
  var chart3 = echarts.init(document.getElementById('chart-regional'), null, { renderer: 'svg' });
  chart3.setOption({
    animation: false,
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      backgroundColor: '#111827',
      borderColor: '#1e293b',
      textStyle: { color: '#e8ecf4', fontSize: 13 },
      formatter: function(p) { return p.name + ': $' + p.value + 'B (' + p.percent + '%)'; }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '55%'],
      data: [
        { value: 109.1, name: 'United States' },
        { value: 9.3, name: 'China' },
        { value: 4.5, name: 'United Kingdom' },
        { value: 7.5, name: 'Rest of World' }
      ],
      label: {
        color: ink,
        fontSize: 12,
        formatter: '{b}\n${c}B'
      },
      labelLine: { lineStyle: { color: rule } },
      itemStyle: {
        borderColor: '#0a0e1a',
        borderWidth: 2
      },
      color: [accent, accent2, muted, accent + '66']
    }]
  });
  window.addEventListener('resize', function() { chart3.resize(); });

  // --- Chart 4: Enterprise AI Adoption Rate (2023-2025) ---
  var chart4 = echarts.init(document.getElementById('chart-adoption'), null, { renderer: 'svg' });
  chart4.setOption({
    animation: false,
    tooltip: tooltipStyle,
    grid: { left: 60, right: 30, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: ['2023', '2024', '2025'],
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      name: 'Adoption Rate (%)',
      nameTextStyle: { color: muted, fontSize: 11 },
      min: 0,
      max: 100,
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: splitLineStyle
    },
    series: [
      {
        name: 'AI Adoption',
        type: 'line',
        data: [55, 78, 88],
        smooth: true,
        symbol: 'circle',
        symbolSize: 10,
        lineStyle: { color: accent, width: 3 },
        itemStyle: { color: accent },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: accent + '40' },
              { offset: 1, color: accent + '05' }
            ]
          }
        },
        label: { show: true, color: ink, fontSize: 12, formatter: '{c}%' }
      },
      {
        name: 'Scaled Deployment',
        type: 'line',
        data: [null, null, 33],
        smooth: true,
        symbol: 'diamond',
        symbolSize: 10,
        lineStyle: { color: accent2, width: 3, type: 'dashed' },
        itemStyle: { color: accent2 },
        label: { show: true, color: ink, fontSize: 12, formatter: '{c}%' }
      }
    ]
  });
  window.addEventListener('resize', function() { chart4.resize(); });

  // --- Chart 5: AI Technology Segment Market Share (IDC 2028 Forecast) ---
  var chart5 = echarts.init(document.getElementById('chart-tech-segments'), null, { renderer: 'svg' });
  chart5.setOption({
    animation: false,
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      backgroundColor: '#111827',
      borderColor: '#1e293b',
      textStyle: { color: '#e8ecf4', fontSize: 13 },
      formatter: function(p) { return p.name + ': ' + p.value + '%'; }
    },
    series: [{
      type: 'pie',
      radius: ['35%', '68%'],
      center: ['50%', '55%'],
      roseType: false,
      data: [
        { value: 35, name: 'Generative AI' },
        { value: 20, name: 'AI Chips / Hardware' },
        { value: 15, name: 'Computer Vision' },
        { value: 12, name: 'NLP / LLM Platforms' },
        { value: 10, name: 'AI Agents / Robotics' },
        { value: 8, name: 'Edge AI / Other' }
      ],
      label: {
        color: ink,
        fontSize: 11,
        formatter: '{b}\n{c}%'
      },
      labelLine: { lineStyle: { color: rule } },
      itemStyle: {
        borderColor: '#0a0e1a',
        borderWidth: 2
      },
      color: [accent, accent2, muted, accent + '99', accent2 + '99', accent + '66']
    }]
  });
  window.addEventListener('resize', function() { chart5.resize(); });

  // --- Chart 6: AI Application Verticals — Investment Share ---
  var chart6 = echarts.init(document.getElementById('chart-verticals'), null, { renderer: 'svg' });
  chart6.setOption({
    animation: false,
    tooltip: tooltipStyle,
    grid: { left: 140, right: 40, top: 30, bottom: 30 },
    xAxis: {
      type: 'value',
      name: 'Share of AI Investment (%)',
      nameTextStyle: { color: muted, fontSize: 11 },
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: splitLineStyle
    },
    yAxis: {
      type: 'category',
      data: [
        'Software & IT Services',
        'Telecommunications',
        'Banking & Finance',
        'Healthcare & Life Sciences',
        'Retail & E-Commerce',
        'Manufacturing & Industrial',
        'Cybersecurity',
        'Agriculture'
      ],
      axisLabel: { color: ink, fontSize: 11 },
      axisLine: axisLineStyle,
      splitLine: { show: false }
    },
    series: [{
      type: 'bar',
      data: [
        { value: 49.8, itemStyle: { color: accent } },
        { value: 7.4, itemStyle: { color: accent2 } },
        { value: 5.8, itemStyle: { color: accent2 } },
        { value: 4.5, itemStyle: { color: accent + '99' } },
        { value: 3.8, itemStyle: { color: accent + '99' } },
        { value: 3.2, itemStyle: { color: accent + '66' } },
        { value: 2.8, itemStyle: { color: accent + '66' } },
        { value: 1.5, itemStyle: { color: muted } }
      ],
      barWidth: '55%',
      label: {
        show: true,
        position: 'right',
        color: ink,
        fontSize: 11,
        formatter: '{c}%'
      }
    }]
  });
  window.addEventListener('resize', function() { chart6.resize(); });

  // --- Chart 7: AI Market Segment CAGR Comparison ---
  var chart7 = echarts.init(document.getElementById('chart-cagr'), null, { renderer: 'svg' });
  chart7.setOption({
    animation: false,
    tooltip: tooltipStyle,
    grid: { left: 140, right: 40, top: 30, bottom: 30 },
    xAxis: {
      type: 'value',
      name: 'CAGR (%)',
      nameTextStyle: { color: muted, fontSize: 11 },
      axisLabel: axisLabelStyle,
      axisLine: axisLineStyle,
      splitLine: splitLineStyle
    },
    yAxis: {
      type: 'category',
      data: [
        'Humanoid Robot Actuators',
        'Generative AI Market',
        'Healthcare AI Market',
        'Asia-Pacific AI Spending',
        'Global AI IT Investment',
        'AI Software Market',
        'AI Semiconductor Revenue',
        'Computer Vision Solutions'
      ],
      axisLabel: { color: ink, fontSize: 11 },
      axisLine: axisLineStyle,
      splitLine: { show: false }
    },
    series: [{
      type: 'bar',
      data: [
        { value: 80.0, itemStyle: { color: accent } },
        { value: 63.8, itemStyle: { color: accent } },
        { value: 45.0, itemStyle: { color: accent2 } },
        { value: 38.0, itemStyle: { color: accent2 } },
        { value: 32.9, itemStyle: { color: accent2 + 'cc' } },
        { value: 29.2, itemStyle: { color: accent2 + 'cc' } },
        { value: 30.0, itemStyle: { color: accent2 + 'cc' } },
        { value: 7.0, itemStyle: { color: muted } }
      ],
      barWidth: '55%',
      label: {
        show: true,
        position: 'right',
        color: ink,
        fontSize: 11,
        formatter: '{c}%'
      }
    }]
  });
  window.addEventListener('resize', function() { chart7.resize(); });

})();
